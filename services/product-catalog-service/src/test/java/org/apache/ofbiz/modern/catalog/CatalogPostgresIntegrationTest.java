/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0.
 */
package org.apache.ofbiz.modern.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = "catalog.ingestion-key=test-key")
@EnabledIfEnvironmentVariable(named = "CATALOG_TEST_DATABASE_URL", matches = ".+")
class CatalogPostgresIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("CATALOG_TEST_DATABASE_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("CATALOG_TEST_DATABASE_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("CATALOG_TEST_DATABASE_PASSWORD"));
    }

    @Autowired CatalogProjectionService projection;
    @Autowired CatalogRepository repository;

    @Test
    void replayIsMonotonicAndSnapshotRemovesOnlyMissingOldRows() {
        CatalogProduct current = product("A", "Current", "2026-09-24T10:00:00Z");
        CatalogProduct missing = product("B", "Missing", "2026-09-24T10:00:00Z");
        projection.apply(List.of(current, missing), "cursor-1");
        projection.apply(List.of(product("A", "Stale replay", "2026-09-23T10:00:00Z")), "cursor-1");
        assertThat(repository.find("A").productName()).isEqualTo("Current");

        projection.recordSnapshotPage("run-1", List.of("A"));
        assertThat(projection.finishSnapshot("run-1", Instant.parse("2026-09-24T11:00:00Z"), 1)).isEqualTo(1);
        assertThat(repository.find("A")).isNotNull();
        assertThat(repository.find("B")).isNull();
        assertThat(repository.latestReconciliation()).containsEntry("sourceCount", 1L);
    }

    private static CatalogProduct product(String id, String name, String updated) {
        return new CatalogProduct(id, name, name, "FINISHED_GOOD", "PRODUCT_ACTIVE", name,
                Instant.parse(updated), Integer.toHexString((id + name + updated).hashCode()));
    }
}
