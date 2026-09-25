package org.apache.ofbiz.modern.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

    private ProjectionBatch snapshot(String runId, String cursor, String productName) {
        var category = new CategoryRecord("100", null, "Gizmos", null, null, BigDecimal.ONE,
                Instant.EPOCH, null, CHECKSUM);
        var product = new ProductRecord("GZ-1000", productName, "Small", null,
                "FINISHED_GOOD", false, false, productName.equals("Tiny Gizmo") ? CHECKSUM : "b".repeat(64));
        var membership = new MembershipRecord("100", "GZ-1000", BigDecimal.ONE, Instant.EPOCH, null);
        return new ProjectionBatch("ofbiz", cursor, runId, List.of(category), List.of(product), List.of(membership));
    }
}
