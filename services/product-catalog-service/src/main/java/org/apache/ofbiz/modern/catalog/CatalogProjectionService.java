/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0.
 */
package org.apache.ofbiz.modern.catalog;

import java.util.List;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CatalogProjectionService {
    private final CatalogRepository repository;
    CatalogProjectionService(CatalogRepository repository) { this.repository = repository; }

    @Transactional
    void apply(List<CatalogProduct> products, String cursor) {
        products.forEach(repository::upsert);
        repository.checkpoint(cursor);
    }

    @Transactional
    void recordSnapshotPage(String runId, List<String> productIds) {
        repository.recordSnapshotIds(runId, productIds);
    }

    @Transactional
    long finishSnapshot(String runId, Instant cutoff, long sourceCount) {
        return repository.finishSnapshot(runId, cutoff, sourceCount);
    }
}
