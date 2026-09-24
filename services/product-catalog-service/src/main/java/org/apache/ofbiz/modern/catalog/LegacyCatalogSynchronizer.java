/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0.
 */
package org.apache.ofbiz.modern.catalog;

import java.util.List;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "catalog.legacy-sync.enabled", havingValue = "true")
class LegacyCatalogSynchronizer {
    private static final Logger LOG = LoggerFactory.getLogger(LegacyCatalogSynchronizer.class);
    private final RestClient legacy;
    private final CatalogRepository repository;
    private final CatalogProjectionService projection;
    private final int pageSize;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Counter syncSuccess;
    private final Counter syncFailure;
    private final Counter reconciledDeletions;

    LegacyCatalogSynchronizer(RestClient.Builder builder, CatalogRepository repository,
            CatalogProjectionService projection, @Value("${catalog.legacy-sync.base-url}") String baseUrl,
            @Value("${catalog.legacy-sync.export-key}") String exportKey,
            @Value("${catalog.legacy-sync.page-size:200}") int pageSize, MeterRegistry meters) {
        this.legacy = builder.baseUrl(baseUrl).defaultHeader("X-Catalog-Export-Key", exportKey).build();
        this.repository = repository;
        this.projection = projection;
        this.pageSize = pageSize;
        this.syncSuccess = meters.counter("catalog.projection.sync", "result", "success");
        this.syncFailure = meters.counter("catalog.projection.sync", "result", "failure");
        this.reconciledDeletions = meters.counter("catalog.reconciliation.deleted");
    }

    @Scheduled(fixedDelayString = "${catalog.legacy-sync.interval:PT30S}")
    void synchronize() {
        if (!running.compareAndSet(false, true)) return;
        try {
            String cursor = repository.checkpoint();
            if ("not-started".equals(cursor)) cursor = "";
            int pages = 0;
            boolean more;
            do {
                String requestCursor = cursor;
                ExportPage page = legacy.get().uri(builder -> builder
                        .path("/catalog/control/modernProductProjectionExport")
                        .queryParam("after", requestCursor).queryParam("limit", pageSize).build())
                        .retrieve().body(ExportPage.class);
                if (page == null) throw new IllegalStateException("legacy catalog export returned no body");
                projection.apply(page.items(), page.cursor());
                cursor = page.cursor();
                more = page.hasMore();
                pages++;
            } while (more && pages < 1000);
            if (more) LOG.warn("catalog_sync_page_limit cursor={}", cursor);
            else {
                syncSuccess.increment();
                LOG.info("catalog_sync_complete cursor={} pages={}", cursor, pages);
            }
        } catch (RuntimeException error) {
            syncFailure.increment();
            LOG.error("catalog_sync_failed", error);
            throw error;
        } finally {
            running.set(false);
        }
    }

    @Scheduled(cron = "${catalog.legacy-sync.snapshot-cron:0 15 2 * * *}")
    void reconcileSnapshot() {
        if (!running.compareAndSet(false, true)) return;
        String runId = UUID.randomUUID().toString();
        Instant cutoff = Instant.now();
        String cursor = "";
        long sourceCount = 0;
        try {
            boolean more;
            do {
                String requestCursor = cursor;
                SnapshotPage page = legacy.get().uri(builder -> builder
                        .path("/catalog/control/modernProductProjectionSnapshot")
                        .queryParam("afterId", requestCursor).queryParam("cutoff", cutoff)
                        .queryParam("limit", pageSize).build()).retrieve().body(SnapshotPage.class);
                if (page == null) throw new IllegalStateException("legacy catalog snapshot returned no body");
                projection.recordSnapshotPage(runId, page.productIds());
                sourceCount += page.productIds().size();
                cursor = page.cursor();
                more = page.hasMore();
            } while (more);
            long deleted = projection.finishSnapshot(runId, cutoff, sourceCount);
            reconciledDeletions.increment(deleted);
            LOG.info("catalog_reconciliation_complete run_id={} source_count={} deleted_count={}",
                    runId, sourceCount, deleted);
        } finally {
            running.set(false);
        }
    }

    record ExportPage(List<CatalogProduct> items, String cursor, boolean hasMore) { }
    record SnapshotPage(List<String> productIds, String cursor, Instant cutoff, boolean hasMore) { }
}
