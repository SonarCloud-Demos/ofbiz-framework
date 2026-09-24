/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0.
 */
package org.apache.ofbiz.modern.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CatalogControllerTest {
    private final CatalogRepository repository = mock(CatalogRepository.class);
    private final CatalogProjectionService projection = mock(CatalogProjectionService.class);
    private final CatalogController controller = new CatalogController(repository, projection, "secret-key");
    private final CatalogProduct product = new CatalogProduct("P-1", "internal", "Product one",
            "FINISHED_GOOD", "PRODUCT_ACTIVE", "Description", Instant.parse("2026-01-01T00:00:00Z"), "abc123");

    @Test
    void searchIsDeterministicallyDelegatedAndPaged() {
        when(repository.search("one", null, "PRODUCT_ACTIVE", 20, 0)).thenReturn(List.of(product));
        var result = controller.search("one", null, "PRODUCT_ACTIVE", 20, 0);
        assertThat(result.get("items")).isEqualTo(List.of(product));
        assertThat(result.get("hasMore")).isEqualTo(false);
    }

    @Test
    void ingestionRejectsWrongKeyAndUpsertsWithCorrectKey() {
        assertThatThrownBy(() -> controller.ingest("wrong", "cursor-1", List.of(product)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(401);
        controller.ingest("secret-key", "cursor-1", List.of(product));
        verify(projection).apply(List.of(product), "cursor-1");
    }

    @Test
    void missingProductIsNotFound() {
        assertThatThrownBy(() -> controller.detail("missing"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(404);
    }
}
