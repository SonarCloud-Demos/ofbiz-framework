package org.apache.ofbiz.modern.platform;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** Dependency-free platform probe; it owns no business capability. */
public final class PlatformSampleApplication {
    private PlatformSampleApplication() { }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8081"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health/live", exchange -> json(exchange, 200, "{\"status\":\"UP\"}"));
        server.createContext("/health/ready", exchange -> json(exchange, 200, "{\"status\":\"READY\"}"));
        server.createContext("/api/platform", exchange -> {
            String correlationId = exchange.getRequestHeaders().getFirst("X-Correlation-ID");
            if (correlationId == null || correlationId.isBlank()) correlationId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().set("X-Correlation-ID", correlationId);
            json(exchange, 200, response(correlationId, Instant.now()));
        });
        server.start();
        System.out.println("{\"level\":\"INFO\",\"event\":\"platform-sample.started\",\"port\":" + port + "}");
    }

    static String response(String correlationId, Instant now) {
        return "{\"service\":\"platform-sample\",\"status\":\"ready\",\"correlationId\":\""
                + correlationId + "\",\"timestamp\":\"" + now + "\"}";
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
