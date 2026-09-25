package org.apache.ofbiz.modern.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.CategoryRecord;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.MembershipRecord;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.ProductRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

interface LegacyCatalogExportClient {
    ExportPage fetch(String recordType, String cursor, Instant cutoff);

    record ExportPage(
            List<CategoryRecord> categories,
            List<ProductRecord> products,
            List<MembershipRecord> memberships,
            String nextCursor,
            boolean hasMore) {
        int size() { return categories.size() + products.size() + memberships.size(); }
    }
}

@Component
@ConditionalOnProperty(name = "catalog.backfill.enabled", havingValue = "true")
final class AdapterCatalogExportClient implements LegacyCatalogExportClient {
    private final RestClient adapter;
    private final ObjectMapper mapper;
    private final byte[] workloadToken;
    private final int pageSize;

    AdapterCatalogExportClient(
            RestClient.Builder builder,
            ObjectMapper mapper,
            @Value("${catalog.backfill.adapter-url}") String adapterUrl,
            @Value("${catalog.backfill.workload-token}") String workloadToken,
            @Value("${catalog.backfill.page-size:200}") int pageSize) {
        if (workloadToken.length() < 64) throw new IllegalStateException("catalog adapter workload token must contain at least 64 characters");
        if (pageSize < 1 || pageSize > 500) throw new IllegalStateException("catalog backfill page size must be between 1 and 500");
        this.adapter = builder.baseUrl(adapterUrl).build();
        this.mapper = mapper;
        this.workloadToken = workloadToken.getBytes(StandardCharsets.UTF_8);
        this.pageSize = pageSize;
    }

    @Override
    public ExportPage fetch(String recordType, String cursor, Instant cutoff) {
        JsonNode root = adapter.get().uri(uri -> {
            var query = uri.path("/internal/catalog-export").queryParam("recordType", recordType)
                    .queryParam("limit", pageSize).queryParam("cutoff", cutoff.toString());
            if (cursor != null) query.queryParam("cursor", cursor);
            return query.build();
        }).header("X-Workload-Token", new String(workloadToken, StandardCharsets.UTF_8))
                .retrieve().body(JsonNode.class);
        JsonNode payload = payload(root);
        JsonNode items = payload.path("items");
        if (!items.isArray()) throw new IllegalStateException("legacy export response has no items array");
        if (!cutoff.toString().equals(payload.path("cutoff").asText())) {
            throw new IllegalStateException("legacy export changed the run cutoff");
        }
        List<CategoryRecord> categories = List.of();
        List<ProductRecord> products = List.of();
        List<MembershipRecord> memberships = List.of();
        if ("categories".equals(recordType)) categories = convert(items, CategoryRecord.class);
        else if ("products".equals(recordType)) products = convert(items, ProductRecord.class);
        else if ("memberships".equals(recordType)) memberships = convert(items, MembershipRecord.class);
        else throw new IllegalArgumentException("unsupported export record type");
        String next = payload.path("nextCursor").isTextual() ? payload.path("nextCursor").asText() : null;
        boolean more = payload.path("hasMore").asBoolean(false);
        if (more && (next == null || next.isBlank())) throw new IllegalStateException("legacy export omitted next cursor");
        return new ExportPage(categories, products, memberships, next, more);
    }

    static JsonNode payload(JsonNode root) {
        if (root == null) throw new IllegalStateException("legacy export returned no response");
        if (root.has("statusCode") && root.path("statusCode").asInt() >= 400) {
            throw new IllegalStateException("legacy export service rejected the request");
        }
        JsonNode data = root.path("data");
        return data.isObject() ? data : root;
    }

    private <T> List<T> convert(JsonNode items, Class<T> type) {
        return java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .map(item -> mapper.convertValue(item, type)).toList();
    }
}
