package org.apache.ofbiz.modern.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.CategoryRecord;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.MembershipRecord;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.ProductRecord;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.ProjectionBatch;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.w3c.dom.Element;

@Testcontainers(disabledWithoutDocker = true)
class JdbcCatalogRepositoryIntegrationTest {
    private static final String CHECKSUM = "a".repeat(64);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private JdbcClient jdbc;
    private CatalogProjectionService projection;
    private JdbcCatalogRepository repository;

    @BeforeEach
    void prepareDatabase() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("TRUNCATE catalog_snapshot_seen, catalog_reconciliation_run, catalog_projection_checkpoint, "
                + "catalog_category_product, catalog_product, catalog_category CASCADE").update();
        repository = new JdbcCatalogRepository(jdbc);
        projection = new CatalogProjectionService(repository);
    }

    @Test
    void snapshotRoundTripRetainsSeenRowsAndRecordsReconciliation() {
        var batch = snapshot("run-1", "cursor-1", "Tiny Gizmo");
        projection.apply(batch);

        var result = projection.finish("run-1", "ofbiz", 3);

        assertEquals(0L, result.get("deletedCount"));
        assertEquals("Tiny Gizmo", repository.product("GZ-1000").orElseThrow().name());
        assertEquals(1, repository.browse("100", 0, 10, "catalog").size());
        assertEquals(1L, jdbc.sql("SELECT COUNT(*) FROM catalog_reconciliation_run").query(Long.class).single());
        assertEquals(0L, jdbc.sql("SELECT COUNT(*) FROM catalog_snapshot_seen").query(Long.class).single());
    }

    @Test
    void repeatBatchUpdatesChangedRecordsAndIncompleteSnapshotCannotDelete() {
        projection.apply(snapshot("run-1", "cursor-1", "Tiny Gizmo"));
        projection.finish("run-1", "ofbiz", 3);
        projection.apply(snapshot("run-2", "cursor-2", "Updated Gizmo"));

        assertEquals("Updated Gizmo", repository.product("GZ-1000").orElseThrow().name());
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> projection.finish("run-2", "ofbiz", 4));
        assertTrue(repository.product("GZ-1000").isPresent());
        assertEquals(0L, jdbc.sql("SELECT COUNT(*) FROM catalog_reconciliation_run WHERE run_id = :runId")
                .param("runId", "run-2").query(Long.class).single());
    }

    @Test
    void committedDemoGoldenCasesMatchModernQueries() throws Exception {
        JsonNode golden = new ObjectMapper().readTree(new File(System.getProperty("catalogGoldenFixture")));
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var document = factory.newDocumentBuilder().parse(new File(System.getProperty("catalogGoldenSource")));

        var categoryNodes = document.getElementsByTagName("ProductCategory");
        for (int index = 0; index < categoryNodes.getLength(); index++) {
            var node = (Element) categoryNodes.item(index);
            repository.upsert(new CategoryRecord(attribute(node, "productCategoryId"),
                    nullable(node, "primaryParentCategoryId"), first(node, "categoryName", "description", "productCategoryId"),
                    firstNullable(node, "longDescription", "description"), nullable(node, "categoryImageUrl"),
                    null, null, null, CHECKSUM));
        }
        var productNodes = document.getElementsByTagName("Product");
        for (int index = 0; index < productNodes.getLength(); index++) {
            var node = (Element) productNodes.item(index);
            repository.upsert(new ProductRecord(attribute(node, "productId"),
                    first(node, "productName", "internalName", "productId"), nullable(node, "description"),
                    nullable(node, "smallImageUrl"), firstOr(node, "productTypeId", "UNKNOWN"),
                    "Y".equals(attribute(node, "isVirtual")), "Y".equals(attribute(node, "isVariant")), CHECKSUM));
        }
        var membershipNodes = document.getElementsByTagName("ProductCategoryMember");
        for (int index = 0; index < membershipNodes.getLength(); index++) {
            var node = (Element) membershipNodes.item(index);
            if (!"100".equals(attribute(node, "productCategoryId"))) continue;
            repository.upsert(new MembershipRecord("100", attribute(node, "productId"),
                    decimal(node, "sequenceNum"), timestamp(node, "fromDate"), timestampNullable(node, "thruDate")));
        }

        JsonNode category = golden.path("categories").get(0);
        assertEquals(strings(category.path("childCategoryIds")), repository.childCategories(category.path("categoryId").asText())
                .stream().map(value -> value.categoryId()).toList());
        JsonNode browse = golden.path("browseCases").get(0);
        assertEquals(strings(browse.path("expectedProductIds")), repository.browse(browse.path("categoryId").asText(), 0, 100,
                browse.path("sort").asText()).stream().map(value -> value.productId()).toList());
        for (JsonNode search : golden.path("searchCases")) {
            assertEquals(strings(search.path("expectedProductIds")), repository.search(search.path("query").asText(), null, 0, 100,
                    search.path("sort").asText()).stream().map(value -> value.productId()).toList(),
                    "search query " + search.path("query").asText());
        }
    }

    private ProjectionBatch snapshot(String runId, String cursor, String productName) {
        var category = new CategoryRecord("100", null, "Gizmos", null, null, BigDecimal.ONE,
                Instant.EPOCH, null, CHECKSUM);
        var product = new ProductRecord("GZ-1000", productName, "Small", null,
                "FINISHED_GOOD", false, false, productName.equals("Tiny Gizmo") ? CHECKSUM : "b".repeat(64));
        var membership = new MembershipRecord("100", "GZ-1000", BigDecimal.ONE, Instant.EPOCH, null);
        return new ProjectionBatch("ofbiz", cursor, runId, List.of(category), List.of(product), List.of(membership));
    }

    private static List<String> strings(JsonNode values) {
        var result = new ArrayList<String>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    private static String attribute(Element node, String name) {
        return node.getAttribute(name);
    }

    private static String nullable(Element node, String name) {
        String value = attribute(node, name);
        return value.isBlank() ? null : value;
    }

    private static String first(Element node, String... names) {
        for (String name : names) {
            String value = attribute(node, name);
            if (!value.isBlank()) return value;
        }
        throw new IllegalArgumentException("missing required fixture field");
    }

    private static String firstNullable(Element node, String... names) {
        for (String name : names) {
            String value = nullable(node, name);
            if (value != null) return value;
        }
        return null;
    }

    private static String firstOr(Element node, String name, String fallback) {
        String value = nullable(node, name);
        return value == null ? fallback : value;
    }

    private static BigDecimal decimal(Element node, String name) {
        String value = nullable(node, name);
        return value == null ? null : new BigDecimal(value);
    }

    private static Instant timestamp(Element node, String name) {
        return Timestamp.valueOf(attribute(node, name)).toInstant();
    }

    private static Instant timestampNullable(Element node, String name) {
        String value = nullable(node, name);
        return value == null ? null : Timestamp.valueOf(value).toInstant();
    }
}
