package org.apache.ofbiz.modern.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.CategoryRecord;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.MembershipRecord;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.ProductRecord;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.ProjectionBatch;
import org.junit.jupiter.api.Test;

class CatalogProjectionServiceTest {
    private static final String CHECKSUM = "a".repeat(64);
    private final CatalogRepository repository = mock(CatalogRepository.class);
    private final CatalogProjectionService service = new CatalogProjectionService(repository);

    @Test
    void batchUpsertsRecordsMarksSnapshotAndAdvancesCheckpointAtomically() {
        var category = new CategoryRecord("100", "CATALOG1", "Gizmos", null, null, null, null, null, CHECKSUM);
        var product = new ProductRecord("GZ-1000", "Tiny Gizmo", null, null, "FINISHED_GOOD", false, false, CHECKSUM);
        var membership = new MembershipRecord("100", "GZ-1000", null, Instant.EPOCH, null);
        var batch = new ProjectionBatch("ofbiz", "cursor-1", "snapshot-1",
                List.of(category), List.of(product), List.of(membership));

        var result = service.apply(batch);

        verify(repository).upsert(category);
        verify(repository).upsert(product);
        verify(repository).upsert(membership);
        verify(repository).recordSnapshot("snapshot-1", "category", "100");
        verify(repository).recordSnapshot("snapshot-1", "product", "GZ-1000");
        verify(repository).recordSnapshot("snapshot-1", "membership", membership.recordId());
        verify(repository).checkpoint("ofbiz", "cursor-1");
        assertEquals(1, result.get("products"));
    }

    @Test
    void incompleteSnapshotCannotDeleteProjectionRows() {
        when(repository.snapshotRecordCount("snapshot-2")).thenReturn(2L);
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.finish("snapshot-2", "ofbiz", 3));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
                .finishSnapshot("snapshot-2", "ofbiz", 3);
    }
}
