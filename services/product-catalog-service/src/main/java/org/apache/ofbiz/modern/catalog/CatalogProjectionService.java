package org.apache.ofbiz.modern.catalog;

import java.util.Map;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.ProjectionBatch;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class CatalogProjectionService {
    private final CatalogRepository repository;

    CatalogProjectionService(CatalogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    Map<String, Object> apply(ProjectionBatch batch) {
        batch.categories().forEach(value -> {
            repository.upsert(value);
            seen(batch.snapshotRunId(), "category", value.categoryId());
        });
        batch.products().forEach(value -> {
            repository.upsert(value);
            seen(batch.snapshotRunId(), "product", value.productId());
        });
        batch.memberships().forEach(value -> {
            repository.upsert(value);
            seen(batch.snapshotRunId(), "membership", value.recordId());
        });
        repository.checkpoint(batch.source(), batch.cursor());
        return Map.of("categories", batch.categories().size(), "products", batch.products().size(),
                "memberships", batch.memberships().size(), "cursor", batch.cursor());
    }

    @Transactional
    Map<String, Object> finish(String runId, String source, long sourceCount) {
        long receivedCount = repository.snapshotRecordCount(runId);
        if (receivedCount != sourceCount) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Snapshot is incomplete: expected " + sourceCount + " records but received " + receivedCount);
        }
        long deleted = repository.finishSnapshot(runId, source, sourceCount);
        return Map.of("runId", runId, "sourceCount", sourceCount, "deletedCount", deleted);
    }

    private void seen(String runId, String type, String id) {
        if (runId != null && !runId.isBlank()) repository.recordSnapshot(runId, type, id);
    }
}
