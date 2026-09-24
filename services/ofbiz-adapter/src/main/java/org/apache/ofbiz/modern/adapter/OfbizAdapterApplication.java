package org.apache.ofbiz.modern.adapter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/** Transitional narrow adapter; no generic entity or database access is exposed. */
public final class OfbizAdapterApplication {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final Semaphore BULKHEAD = new Semaphore(16);
    private static final AtomicInteger FAILURES = new AtomicInteger();
    private static volatile Instant circuitOpenUntil = Instant.EPOCH;
    private OfbizAdapterApplication() { }

    public static void main(String[] args) throws IOException {
        Config config = new Config(URI.create(env("IDENTITY_EXCHANGE_URL", "http://identity-access:8083/internal/exchanges")),
                URI.create(env("OFBIZ_ORIGIN", "https://legacy-ofbiz:8443")), required("WORKLOAD_TOKEN"), required("OFBIZ_JWT_KEY"),
                URI.create(env("OFBIZ_REST_ORIGIN", "https://legacy-ofbiz:8443/rest")), nonBlank("OFBIZ_REST_USER_LOGIN_ID"));
        HttpServer server = HttpServer.create(new InetSocketAddress(Integer.parseInt(env("PORT", "8084"))), 0);
        server.createContext("/health/ready", e -> json(e, 200, "{\"status\":\"READY\"}"));
        server.createContext("/legacy-session", e -> legacySession(e, config));
        server.createContext("/internal/catalog-export", e -> catalogExport(e, config));
        server.start();
    }

    static String safeLegacyPath(String supplied) {
        return supplied != null && supplied.matches("/[A-Za-z0-9_./-]*") && !supplied.startsWith("//") ? supplied : "/webtools/control/main";
    }
    static String token(String userLoginId, String key, Instant now) {
        return JWT.create().withIssuer("ApacheOFBiz").withClaim("userLoginId", userLoginId)
                .withIssuedAt(Date.from(now)).withExpiresAt(Date.from(now.plusSeconds(30))).sign(Algorithm.HMAC512(key));
    }
    static String rewriteCookie(String source) {
        return source.replaceAll("(?i);\\s*Domain=[^;]+", "").replaceAll("(?i);\\s*Path=[^;]+", "; Path=/legacy/") + "; Secure; HttpOnly; SameSite=Lax";
    }
    static URI legacyRequestUri(URI origin, String path) {
        return URI.create(origin.toString().replaceFirst("/$", "") + safeLegacyPath(path));
    }

    static String catalogRecordType(String supplied) {
        return supplied != null && supplied.matches("categories|products|memberships") ? supplied : null;
    }

    static int catalogPageSize(String supplied) {
        try { return Math.max(1, Math.min(supplied == null ? 200 : Integer.parseInt(supplied), 500)); }
        catch (NumberFormatException invalid) { return -1; }
    }

    private static void catalogExport(HttpExchange exchange, Config config) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) { json(exchange, 405, "{\"error\":\"method_not_allowed\"}"); return; }
        String workload = exchange.getRequestHeaders().getFirst("X-Workload-Token");
        if (workload == null || !MessageDigest.isEqual(workload.getBytes(StandardCharsets.UTF_8),
                config.workloadToken().getBytes(StandardCharsets.UTF_8))) { json(exchange, 401, "{\"error\":\"unauthorized\"}"); return; }
        Map<String, String> parameters = query(exchange.getRequestURI().getRawQuery());
        String type = catalogRecordType(parameters.get("recordType"));
        int limit = catalogPageSize(parameters.get("limit"));
        if (type == null || limit < 1) { json(exchange, 400, "{\"error\":\"invalid_export_request\"}"); return; }
        StringBuilder uri = new StringBuilder(config.restOrigin().toString().replaceFirst("/$", ""))
                .append("/catalog-export/").append(type).append("?limit=").append(limit);
        appendQuery(uri, "cursor", parameters.get("cursor"));
        appendQuery(uri, "cutoff", parameters.get("cutoff"));
        try {
            HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder(URI.create(uri.toString()))
                    .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer "
                            + token(config.restUserLoginId(), config.jwtKey(), Instant.now()))
                    .header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) { json(exchange, 502, "{\"error\":\"legacy_export_unavailable\"}"); return; }
            json(exchange, 200, response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); json(exchange, 502, "{\"error\":\"legacy_export_unavailable\"}");
        } catch (RuntimeException failure) {
            json(exchange, 502, "{\"error\":\"legacy_export_unavailable\"}");
        }
    }

    private static void appendQuery(StringBuilder uri, String name, String value) {
        if (value != null && !value.isBlank() && value.length() <= 512) {
            uri.append('&').append(name).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
    }

    private static void legacySession(HttpExchange exchange, Config config) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) { json(exchange, 405, "{\"error\":\"method_not_allowed\"}"); return; }
        if (circuitOpenUntil.isAfter(Instant.now()) || !BULKHEAD.tryAcquire()) { json(exchange, 503, "{\"error\":\"temporarily_unavailable\"}"); return; }
        Map<String,String> query = query(exchange.getRequestURI().getRawQuery());
        String path = safeLegacyPath(query.get("return"));
        try {
            HttpResponse<String> mappingResponse = CLIENT.send(HttpRequest.newBuilder(URI.create(config.exchange() + "?code=" + URLEncoder.encode(query.getOrDefault("code", ""), StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(3)).header("X-Workload-Token", config.workloadToken()).DELETE().build(), HttpResponse.BodyHandlers.ofString());
            if (mappingResponse.statusCode() != 200) { json(exchange, 401, "{\"error\":\"invalid_exchange\"}"); return; }
            JsonNode mapping = JSON.readTree(mappingResponse.body());
            HttpResponse<Void> legacy = CLIENT.send(HttpRequest.newBuilder(legacyRequestUri(config.origin(), path)).timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + token(mapping.path("userLoginId").asText(), config.jwtKey(), Instant.now()))
                    .header("X-Correlation-ID", java.util.UUID.randomUUID().toString()).GET().build(), HttpResponse.BodyHandlers.discarding());
            if (legacy.statusCode() >= 500 || legacy.headers().allValues("set-cookie").isEmpty()) throw new IOException("legacy session unavailable");
            legacy.headers().allValues("set-cookie").forEach(value -> exchange.getResponseHeaders().add("Set-Cookie", rewriteCookie(value)));
            exchange.getResponseHeaders().set("Location", "/legacy" + path);
            exchange.sendResponseHeaders(302, -1); exchange.close(); FAILURES.set(0);
            System.out.println("{\"level\":\"INFO\",\"event\":\"adapter.legacy_session_created\",\"partyId\":\"" + mapping.path("partyId").asText() + "\"}");
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); unavailable(exchange); }
        catch (Exception failure) { unavailable(exchange); }
        finally { BULKHEAD.release(); }
    }
    private static void unavailable(HttpExchange exchange) throws IOException { if (FAILURES.incrementAndGet() >= 3) circuitOpenUntil = Instant.now().plusSeconds(30); json(exchange, 502, "{\"error\":\"legacy_unavailable\"}"); }
    private static Map<String,String> query(String raw) { Map<String,String> values = new HashMap<>(); if (raw != null) for (String item : raw.split("&")) { String[] pair=item.split("=",2); values.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8), pair.length==2?URLDecoder.decode(pair[1],StandardCharsets.UTF_8):""); } return values; }
    private static String env(String name,String fallback){return System.getenv().getOrDefault(name,fallback);} private static String required(String name){String value=System.getenv(name);if(value==null||value.length()<64)throw new IllegalStateException(name+" must contain at least 64 characters");return value;} private static String nonBlank(String name){String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalStateException(name+" must not be blank");return value;}
    private static void json(HttpExchange e,int status,String body)throws IOException{byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.getResponseHeaders().set("Cache-Control","no-store");e.sendResponseHeaders(status,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}}
    record Config(URI exchange, URI origin, String workloadToken, String jwtKey, URI restOrigin, String restUserLoginId) { }
}
