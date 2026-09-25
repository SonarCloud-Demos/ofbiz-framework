package org.apache.ofbiz.modern.catalog;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.ProjectionBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "catalog.backfill.enabled", havingValue = "true")
final class CatalogBackfillCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(CatalogBackfillCoordinator.class);
    private static final java.util.List<String> RECORD_TYPES = java.util.List.of("categories", "products", "memberships");
    private final LegacyCatalogExportClient exporter;
    private final CatalogProjectionService projection;
    private final boolean reconcileEnabled;
    private final AtomicBoolean running = new AtomicBoolean();

    CatalogBackfillCoordinator(
            LegacyCatalogExportClient exporter,
            CatalogProjectionService projection,
            @Value("${catalog.backfill.reconcile-enabled:false}") boolean reconcileEnabled) {
        this.exporter = exporter;
        this.projection = projection;
        this.reconcileEnabled = reconcileEnabled;
    }

    @Scheduled(fixedDelayString = "${catalog.backfill.interval:PT5M}")
    void scheduledBackfill() {
        if (!running.compareAndSet(false, true)) {
            LOG.warn("catalog.backfill.skipped reason=already_running");
            return;
        }
        try {
            RunResult result = runOnce();
            LOG.info("catalog.backfill.completed runId={} records={} reconciled={}",
                    result.runId(), result.recordCount(), result.reconciled());
        } catch (RuntimeException failure) {
            LOG.error("catalog.backfill.failed type={}", failure.getClass().getSimpleName(), failure);
        } finally {
            running.set(false);
        }
    }

    RunResult runOnce() {
        String runId = UUID.randomUUID().toString();
        Instant cutoff = Instant.now();
        long recordCount = 0;
        for (String type : RECORD_TYPES) {
            String cursor = null;
            int pages = 0;
            do {
                var page = exporter.fetch(type, cursor, cutoff);
                String checkpoint = type + ":" + (page.nextCursor() == null ? "complete" : page.nextCursor());
                projection.apply(new ProjectionBatch("ofbiz", checkpoint, reconcileEnabled ? runId : null,
                        page.categories(), page.products(), page.memberships()));
                recordCount += page.size();
                if (page.hasMore() && java.util.Objects.equals(cursor, page.nextCursor())) {
                    throw new IllegalStateException("legacy export cursor did not advance");
                }
                cursor = page.nextCursor();
                if (++pages > 100_000) throw new IllegalStateException("legacy export exceeded page safety limit");
                if (!page.hasMore()) break;
            } while (true);
        }
        if (reconcileEnabled) projection.finish(runId, "ofbiz", recordCount);
        return new RunResult(runId, cutoff, recordCount, reconcileEnabled);
    }

    record RunResult(String runId, Instant cutoff, long recordCount, boolean reconciled) { }
}
