/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0.
 */
package org.apache.ofbiz.modern.catalog;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/catalog")
@org.springframework.validation.annotation.Validated
class CatalogController {
    private final CatalogRepository repository;
    private final CatalogProjectionService projection;
    private final String ingestionKey;
    CatalogController(CatalogRepository repository, CatalogProjectionService projection,
            @org.springframework.beans.factory.annotation.Value("${catalog.ingestion-key}") String ingestionKey) {
        this.repository = repository;
        this.projection = projection;
        this.ingestionKey = ingestionKey;
    }

    @GetMapping("/products")
    Map<String, Object> search(@RequestParam(required = false) @Size(max = 255) String q,
            @RequestParam(required = false) String type, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        List<CatalogProduct> items = repository.search(q, type, status, limit, offset);
        return Map.of("items", items, "limit", limit, "offset", offset, "hasMore", items.size() == limit);
    }

    @GetMapping("/products/{id}")
    CatalogProduct detail(@PathVariable @Size(max = 64) String id) {
        CatalogProduct product = repository.find(id);
        if (product == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found");
        return product;
    }

    @PostMapping("/projection/products")
    @Transactional
    Map<String, Object> ingest(@RequestHeader("X-Catalog-Ingestion-Key") String supplied,
            @RequestHeader("X-Catalog-Source-Cursor") String cursor,
            @RequestBody @Size(max = 1000) List<@Valid CatalogProduct> products) {
        if (!java.security.MessageDigest.isEqual(supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                ingestionKey.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        projection.apply(products, cursor);
        return Map.of("accepted", products.size(), "sourceCursor", cursor);
    }

    @GetMapping("/reconciliation")
    Map<String, Object> reconciliation() {
        return Map.of("projectedCount", repository.count(), "sourceCursor", repository.checkpoint(),
                "latestSnapshot", repository.latestReconciliation(),
                "comparisonOwner", "migration-data-owner");
    }
}
