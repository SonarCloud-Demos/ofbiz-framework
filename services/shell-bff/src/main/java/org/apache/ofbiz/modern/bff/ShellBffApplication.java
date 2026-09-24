package org.apache.ofbiz.modern.bff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

/** OIDC-backed same-origin browser boundary for the Phase 3 platform test route. */
public final class ShellBffApplication {
    private static final Pattern CORRELATION = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Pattern RETURN_PATH = Pattern.compile("/[-A-Za-z0-9_./]*");
    private static final Pattern CATALOG_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Semaphore BULKHEAD = new Semaphore(32);
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private static final Map<String, LoginAttempt> ATTEMPTS = new ConcurrentHashMap<>();
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    private ShellBffApplication() { }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(env("PORT", "8082"));
        Config config = new Config(URI.create(env("PLATFORM_SERVICE_URL", "http://platform-sample:8081/api/platform")),
                URI.create(env("CATALOG_SERVICE_URL", "http://product-catalog:8080/api/catalog/v1")),
                URI.create(env("OIDC_AUTHORIZATION_ENDPOINT", "http://localhost:8180/realms/ofbiz-development/protocol/openid-connect/auth")),
                URI.create(env("OIDC_TOKEN_ENDPOINT", "http://identity:8080/realms/ofbiz-development/protocol/openid-connect/token")),
                URI.create(env("OIDC_USERINFO_ENDPOINT", "http://identity:8080/realms/ofbiz-development/protocol/openid-connect/userinfo")),
                env("OIDC_CLIENT_ID", "modern-shell"), env("OIDC_REDIRECT_URI", "http://localhost:8080/auth/callback"),
                Set.of(env("TRUSTED_HOSTS", "localhost").split(",")), Boolean.parseBoolean(env("COOKIE_SECURE", "false")),
                URI.create(env("IDENTITY_EXCHANGE_URL", "http://identity-access:8083/internal/exchanges")), env("WORKLOAD_TOKEN", ""),
                Integer.parseInt(env("PLATFORM_ROUTE_PERCENT", "100")), Boolean.parseBoolean(env("PLATFORM_ROUTE_DISABLED", "false")),
                Integer.parseInt(env("CATALOG_ROUTE_PERCENT", "100")), Boolean.parseBoolean(env("CATALOG_ROUTE_DISABLED", "false")));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health/live", exchange -> json(exchange, 200, "{\"status\":\"UP\"}"));
        server.createContext("/health/ready", exchange -> json(exchange, 200, "{\"status\":\"READY\"}"));
        server.createContext("/auth/login", exchange -> login(exchange, config));
        server.createContext("/auth/callback", exchange -> callback(exchange, config));
        server.createContext("/auth/session", exchange -> session(exchange, config));
        server.createContext("/auth/logout", exchange -> logout(exchange, config));
        server.createContext("/auth/legacy", exchange -> legacy(exchange, config));
        server.createContext("/bff/platform", exchange -> platform(exchange, config));
        server.createContext("/bff/catalog", exchange -> catalog(exchange, config));
        server.start();
        System.out.println("{\"level\":\"INFO\",\"event\":\"shell-bff.started\",\"port\":" + port + "}");
    }

    static boolean trustedHost(String header, Set<String> trustedHosts) {
        if (header == null) return false;
        String host = header.toLowerCase().replaceFirst(":\\d+$", "");
        return trustedHosts.stream().map(String::trim).map(String::toLowerCase).anyMatch(host::equals);
    }

    static String correlationId(String supplied) {
        return supplied != null && CORRELATION.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
    }

    static String safeReturnPath(String supplied) {
        return supplied != null && RETURN_PATH.matcher(supplied).matches() && !supplied.startsWith("//")
                ? supplied : "/modern/platform";
    }

    static boolean csrfMatches(Session session, String supplied) {
        return session != null && supplied != null && MessageDigest.isEqual(
                session.csrf().getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    static boolean cohortEnabled(String subject, int percentage, boolean disabled) {
        if (disabled || percentage <= 0) return false;
        if (percentage >= 100) return true;
        return Math.floorMod(subject.hashCode(), 100) < percentage;
    }

    private static void login(HttpExchange exchange, Config config) throws IOException {
        if (!validRequest(exchange, config, "GET")) return;
        String state = randomToken();
        String verifier = randomToken();
        ATTEMPTS.put(state, new LoginAttempt(verifier, safeReturnPath(query(exchange).get("return")), Instant.now().plusSeconds(300)));
        String challenge = base64(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        redirect(exchange, config.authorization() + "?response_type=code&client_id=" + encode(config.clientId())
                + "&redirect_uri=" + encode(config.redirectUri()) + "&scope=openid%20profile%20email"
                + "&state=" + encode(state) + "&code_challenge=" + encode(challenge) + "&code_challenge_method=S256");
    }

    private static void callback(HttpExchange exchange, Config config) throws IOException {
        if (!validRequest(exchange, config, "GET")) return;
        Map<String, String> query = query(exchange);
        LoginAttempt attempt = ATTEMPTS.remove(query.get("state"));
        if (attempt == null || attempt.expires().isBefore(Instant.now()) || query.get("code") == null) {
            json(exchange, 400, error("invalid_login_response", correlationId(null))); return;
        }
        try {
            String form = "grant_type=authorization_code&client_id=" + encode(config.clientId()) + "&redirect_uri="
                    + encode(config.redirectUri()) + "&code=" + encode(query.get("code")) + "&code_verifier=" + encode(attempt.verifier());
            HttpResponse<String> tokenResponse = CLIENT.send(HttpRequest.newBuilder(config.token()).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode token = JSON.readTree(tokenResponse.body());
            if (tokenResponse.statusCode() != 200 || token.path("access_token").asText().isBlank()) throw new IOException("token rejected");
            HttpResponse<String> userResponse = CLIENT.send(HttpRequest.newBuilder(config.userInfo()).timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + token.path("access_token").asText()).GET().build(), HttpResponse.BodyHandlers.ofString());
            JsonNode user = JSON.readTree(userResponse.body());
            if (userResponse.statusCode() != 200 || user.path("sub").asText().isBlank()) throw new IOException("userinfo rejected");
            Set<String> roles = user.path("roles").isArray()
                    ? JSON.convertValue(user.path("roles"), JSON.getTypeFactory().constructCollectionType(Set.class, String.class))
                    : Set.of();
            String sessionId = randomToken();
            SESSIONS.put(sessionId, new Session(user.path("sub").asText(), user.path("preferred_username").asText("user"),
                    roles, randomToken(), Instant.now().plusSeconds(1800)));
            exchange.getResponseHeaders().add("Set-Cookie", cookie(sessionId, config.secureCookie(), 1800));
            audit("login", user.path("sub").asText(), "success");
            redirect(exchange, attempt.returnPath());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); json(exchange, 503, error("identity_unavailable", correlationId(null)));
        } catch (Exception failure) {
            audit("login", "unknown", "failure"); json(exchange, 502, error("identity_unavailable", correlationId(null)));
        }
    }

    private static void session(HttpExchange exchange, Config config) throws IOException {
        if (!validRequest(exchange, config, "GET")) return;
        Session current = currentSession(exchange);
        if (current == null) { json(exchange, 401, "{\"authenticated\":false}"); return; }
        json(exchange, 200, "{\"authenticated\":true,\"user\":\"" + escape(current.username())
                + "\",\"csrfToken\":\"" + current.csrf() + "\",\"platformRouteEnabled\":"
                + cohortEnabled(current.subject(), config.routePercentage(), config.routeDisabled())
                + ",\"catalogRouteEnabled\":"
                + cohortEnabled(current.subject(), config.catalogRoutePercentage(), config.catalogRouteDisabled()) + "}");
    }

    private static void logout(HttpExchange exchange, Config config) throws IOException {
        if (!validRequest(exchange, config, "POST")) return;
        Session current = currentSession(exchange);
        if (!csrfMatches(current, exchange.getRequestHeaders().getFirst("X-CSRF-Token"))) {
            json(exchange, 403, error("csrf_rejected", correlationId(null))); return;
        }
        String id = cookieValue(exchange, "ofbiz-session");
        SESSIONS.remove(id);
        exchange.getResponseHeaders().add("Set-Cookie", cookie("", config.secureCookie(), 0));
        audit("logout", current.subject(), "success");
        json(exchange, 200, "{\"authenticated\":false}");
    }

    private static void legacy(HttpExchange exchange, Config config) throws IOException {
        if (!validRequest(exchange, config, "GET")) return;
        Session current = currentSession(exchange);
        if (current == null) { redirect(exchange, "/auth/login?return=/auth/legacy"); return; }
        if (!current.roles().contains("development-user")) { json(exchange, 403, error("forbidden", correlationId(null))); return; }
        try {
            String body = "{\"subject\":\"" + escape(current.subject()) + "\"}";
            HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder(config.identityExchange()).timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json").header("X-Workload-Token", config.workloadToken())
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            JsonNode result = JSON.readTree(response.body());
            if (response.statusCode() != 201 || result.path("code").asText().isBlank()) { json(exchange, 403, error("legacy_access_denied", correlationId(null))); return; }
            redirect(exchange, "/legacy-session?code=" + encode(result.path("code").asText()) + "&return=%2Fwebtools%2Fcontrol%2Fmain");
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); json(exchange, 503, error("identity_unavailable", correlationId(null))); }
        catch (Exception failure) { json(exchange, 502, error("identity_unavailable", correlationId(null))); }
    }

    private static void platform(HttpExchange exchange, Config config) throws IOException {
        String correlation = correlationId(exchange.getRequestHeaders().getFirst("X-Correlation-ID"));
        exchange.getResponseHeaders().set("X-Correlation-ID", correlation);
        if (!validRequest(exchange, config, "GET")) return;
        Session current = currentSession(exchange);
        if (current == null) { json(exchange, 401, error("authentication_required", correlation)); return; }
        if (!current.roles().contains("development-user")) { json(exchange, 403, error("forbidden", correlation)); return; }
        if (!cohortEnabled(current.subject(), config.routePercentage(), config.routeDisabled())) { json(exchange, 404, error("route_disabled", correlation)); return; }
        if (!BULKHEAD.tryAcquire()) { json(exchange, 503, error("temporarily_unavailable", correlation)); return; }
        try {
            HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder(config.platform()).timeout(Duration.ofSeconds(3))
                    .header("X-Correlation-ID", correlation).GET().build(), HttpResponse.BodyHandlers.ofString());
            json(exchange, response.statusCode() == 200 ? 200 : 502,
                    response.statusCode() == 200 ? response.body() : error("upstream_unavailable", correlation));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); json(exchange, 503, error("temporarily_unavailable", correlation));
        } catch (Exception failure) { json(exchange, 502, error("upstream_unavailable", correlation)); }
        finally { BULKHEAD.release(); }
    }

    private static void catalog(HttpExchange exchange, Config config) throws IOException {
        String correlation = correlationId(exchange.getRequestHeaders().getFirst("X-Correlation-ID"));
        exchange.getResponseHeaders().set("X-Correlation-ID", correlation);
        if (!validRequest(exchange, config, "GET")) return;
        Session current = currentSession(exchange);
        if (current == null) { json(exchange, 401, error("authentication_required", correlation)); return; }
        if (!current.roles().contains("development-user")) { json(exchange, 403, error("forbidden", correlation)); return; }
        if (!cohortEnabled(current.subject(), config.catalogRoutePercentage(), config.catalogRouteDisabled())) {
            json(exchange, 404, error("route_disabled", correlation)); return;
        }
        URI upstream = catalogUri(config.catalog(), exchange.getRequestURI());
        if (upstream == null) { json(exchange, 400, error("invalid_catalog_request", correlation)); return; }
        if (!BULKHEAD.tryAcquire()) { json(exchange, 503, error("temporarily_unavailable", correlation)); return; }
        try {
            HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder(upstream).timeout(Duration.ofSeconds(3))
                    .header("X-Correlation-ID", correlation).GET().build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            json(exchange, status == 200 || status == 400 || status == 404 ? status : 502,
                    status == 200 || status == 400 || status == 404 ? response.body() : error("upstream_unavailable", correlation));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); json(exchange, 503, error("temporarily_unavailable", correlation));
        } catch (Exception failure) { json(exchange, 502, error("upstream_unavailable", correlation)); }
        finally { BULKHEAD.release(); }
    }

    static URI catalogUri(URI base, URI browserUri) {
        String prefix = "/bff/catalog";
        String path = browserUri.getPath();
        if (!path.startsWith(prefix)) return null;
        String suffix = path.substring(prefix.length());
        boolean category = suffix.matches("/categories/[A-Za-z0-9][A-Za-z0-9_.-]{0,63}");
        boolean browse = suffix.matches("/categories/[A-Za-z0-9][A-Za-z0-9_.-]{0,63}/products");
        boolean product = suffix.matches("/products/[A-Za-z0-9][A-Za-z0-9_.-]{0,63}/summary");
        boolean search = "/search".equals(suffix);
        if (!(category || browse || product || search)) return null;
        Map<String, String> values = query(browserUri);
        Set<String> allowed = search ? Set.of("q", "categoryId", "cursor", "limit", "sort")
                : browse ? Set.of("cursor", "limit", "sort") : Set.of();
        if (!allowed.containsAll(values.keySet())) return null;
        if (search && (values.get("q") == null || values.get("q").isBlank() || values.get("q").length() > 200)) return null;
        if (values.containsKey("categoryId") && !CATALOG_ID.matcher(values.get("categoryId")).matches()) return null;
        if (values.containsKey("cursor") && (values.get("cursor").isBlank() || values.get("cursor").length() > 512
                || !values.get("cursor").matches("[A-Za-z0-9_-]+"))) return null;
        if (values.containsKey("sort") && !values.get("sort").matches("catalog|name")) return null;
        if (values.containsKey("limit")) {
            try { int limit = Integer.parseInt(values.get("limit")); if (limit < 1 || limit > 100) return null; }
            catch (NumberFormatException invalid) { return null; }
        }
        StringBuilder target = new StringBuilder(base.toString().replaceFirst("/$", "")).append(suffix);
        if (!values.isEmpty()) {
            target.append('?');
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) target.append('&');
                target.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
                first = false;
            }
        }
        return URI.create(target.toString());
    }

    private static boolean validRequest(HttpExchange exchange, Config config, String method) throws IOException {
        if (!trustedHost(exchange.getRequestHeaders().getFirst("Host"), config.hosts())) { json(exchange, 400, error("invalid_request", correlationId(null))); return false; }
        if (!method.equals(exchange.getRequestMethod())) { exchange.getResponseHeaders().set("Allow", method); json(exchange, 405, error("method_not_allowed", correlationId(null))); return false; }
        return true;
    }

    private static Session currentSession(HttpExchange exchange) {
        String id = cookieValue(exchange, "ofbiz-session");
        Session value = id == null ? null : SESSIONS.get(id);
        if (value != null && value.expires().isBefore(Instant.now())) { SESSIONS.remove(id); return null; }
        return value;
    }

    static String cookie(String value, boolean secure, int maxAge) {
        return "ofbiz-session=" + value + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAge + (secure ? "; Secure" : "");
    }
    private static String cookieValue(HttpExchange exchange, String name) {
        String header = exchange.getRequestHeaders().getFirst("Cookie");
        if (header == null) return null;
        for (String item : header.split(";")) { String[] pair = item.trim().split("=", 2); if (pair.length == 2 && pair[0].equals(name)) return pair[1]; }
        return null;
    }
    private static Map<String, String> query(HttpExchange exchange) {
        return query(exchange.getRequestURI());
    }
    private static Map<String, String> query(URI uri) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        String raw = uri.getRawQuery();
        if (raw != null) for (String item : raw.split("&")) { String[] pair = item.split("=", 2); String key = java.net.URLDecoder.decode(pair[0], StandardCharsets.UTF_8); if (values.containsKey(key)) values.put("__duplicate__", key); else values.put(key, pair.length == 2 ? java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : ""); }
        return values;
    }
    private static String randomToken() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return base64(bytes); }
    private static byte[] sha256(byte[] value) { try { return MessageDigest.getInstance("SHA-256").digest(value); } catch (Exception impossible) { throw new IllegalStateException(impossible); } }
    private static String base64(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String env(String name, String fallback) { return System.getenv().getOrDefault(name, fallback); }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String error(String code, String correlation) { return "{\"error\":\"" + code + "\",\"correlationId\":\"" + correlation + "\"}"; }
    private static void redirect(HttpExchange exchange, String location) throws IOException { exchange.getResponseHeaders().set("Location", location); exchange.sendResponseHeaders(302, -1); exchange.close(); }
    private static void audit(String event, String subject, String outcome) { System.out.println("{\"level\":\"INFO\",\"event\":\"identity." + event + "\",\"subject\":\"" + escape(subject) + "\",\"outcome\":\"" + outcome + "\"}"); }
    private static void json(HttpExchange exchange, int status, String body) throws IOException { byte[] bytes = body.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8"); exchange.getResponseHeaders().set("Cache-Control", "no-store"); exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff"); exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"); exchange.sendResponseHeaders(status, bytes.length); try (var output = exchange.getResponseBody()) { output.write(bytes); } }

    record Config(URI platform, URI catalog, URI authorization, URI token, URI userInfo, String clientId, String redirectUri,
                  Set<String> hosts, boolean secureCookie, URI identityExchange, String workloadToken,
                  int routePercentage, boolean routeDisabled, int catalogRoutePercentage, boolean catalogRouteDisabled) { }
    record LoginAttempt(String verifier, String returnPath, Instant expires) { }
    record Session(String subject, String username, Set<String> roles, String csrf, Instant expires) { }
}
