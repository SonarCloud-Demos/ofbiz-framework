package org.apache.ofbiz.modern.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Transitional identity-to-Party mapping and one-time legacy exchange issuer. */
public final class IdentityAccessApplication {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, Exchange> EXCHANGES = new ConcurrentHashMap<>();
    private IdentityAccessApplication() { }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(env("PORT", "8083"));
        String secret = required("WORKLOAD_TOKEN");
        Map<String, Mapping> mappings = parseMappings(env("IDENTITY_MAPPINGS", "{}"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health/ready", e -> json(e, 200, "{\"status\":\"READY\"}"));
        server.createContext("/internal/exchanges", e -> exchanges(e, secret, mappings));
        server.start();
    }

    static Map<String, Mapping> parseMappings(String source) throws IOException {
        Map<String, Mapping> result = new java.util.HashMap<>();
        JsonNode root = JSON.readTree(source);
        root.fields().forEachRemaining(item -> result.put(item.getKey(),
                new Mapping(item.getValue().path("principalId").asText(), item.getValue().path("partyId").asText(), item.getValue().path("userLoginId").asText())));
        return Map.copyOf(result);
    }

    static boolean authorized(String supplied, String expected) {
        return supplied != null && MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    static Exchange redeem(Map<String, Exchange> exchanges, String code, Instant now) {
        Exchange value = exchanges.remove(code);
        return value != null && value.expires().isAfter(now) ? value : null;
    }

    private static void exchanges(HttpExchange exchange, String secret, Map<String, Mapping> mappings) throws IOException {
        if (!authorized(exchange.getRequestHeaders().getFirst("X-Workload-Token"), secret)) { json(exchange, 401, "{\"error\":\"unauthorized\"}"); return; }
        if ("POST".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getRawQuery() == null) {
            JsonNode request = JSON.readTree(exchange.getRequestBody());
            Mapping mapping = mappings.get(request.path("subject").asText());
            if (mapping == null || mapping.userLoginId().isBlank() || mapping.partyId().isBlank()) { json(exchange, 403, "{\"error\":\"identity_not_linked\"}"); return; }
            String code = random();
            EXCHANGES.put(code, new Exchange(mapping, Instant.now().plusSeconds(30)));
            audit("legacy_exchange_issued", mapping.principalId());
            json(exchange, 201, "{\"code\":\"" + code + "\",\"expiresIn\":30}");
            return;
        }
        if ("DELETE".equals(exchange.getRequestMethod())) {
            String raw = exchange.getRequestURI().getRawQuery();
            String code = raw != null && raw.startsWith("code=") ? raw.substring(5) : "";
            Exchange value = redeem(EXCHANGES, code, Instant.now());
            if (value == null) { json(exchange, 404, "{\"error\":\"invalid_exchange\"}"); return; }
            Mapping m = value.mapping();
            audit("legacy_exchange_redeemed", m.principalId());
            json(exchange, 200, JSON.writeValueAsString(m));
            return;
        }
        json(exchange, 405, "{\"error\":\"method_not_allowed\"}");
    }
    private static String random() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String env(String name, String fallback) { return System.getenv().getOrDefault(name, fallback); }
    private static String required(String name) { String value = System.getenv(name); if (value == null || value.length() < 32) throw new IllegalStateException(name + " must contain at least 32 characters"); return value; }
    private static void audit(String event, String principal) { System.out.println("{\"level\":\"INFO\",\"event\":\"identity." + event + "\",\"principalId\":\"" + principal + "\"}"); }
    private static void json(HttpExchange e, int status, String body) throws IOException { byte[] bytes = body.getBytes(StandardCharsets.UTF_8); e.getResponseHeaders().set("Content-Type", "application/json"); e.getResponseHeaders().set("Cache-Control", "no-store"); e.sendResponseHeaders(status, bytes.length); try (var out = e.getResponseBody()) { out.write(bytes); } }
    public record Mapping(String principalId, String partyId, String userLoginId) { }
    public record Exchange(Mapping mapping, Instant expires) { }
}
