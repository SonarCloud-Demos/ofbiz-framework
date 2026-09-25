package org.apache.ofbiz.modern.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.CategoryRecord;
import org.apache.ofbiz.modern.catalog.LegacyCatalogExportClient.ExportPage;
import org.junit.jupiter.api.Test;

class CatalogBackfillCoordinatorTest {
    private static final String CHECKSUM = "a".repeat(64);
    private final LegacyCatalogExportClient exporter = mock(LegacyCatalogExportClient.class);
    private final CatalogProjectionService projection = mock(CatalogProjectionService.class);

    @Test
    void pagesTypesInForeignKeyOrderAndReconcilesOnlyAfterCompleteRun() {
        var category = new CategoryRecord("100", null, "Gizmos", null, null, null, null, null, CHECKSUM);
        when(exporter.fetch(org.mockito.ArgumentMatchers.eq("categories"), org.mockito.ArgumentMatchers.nullable(String.class), any()))
                .thenReturn(new ExportPage(List.of(category), List.of(), List.of(), null, false));
        when(exporter.fetch(org.mockito.ArgumentMatchers.eq("products"), org.mockito.ArgumentMatchers.nullable(String.class), any()))
                .thenReturn(new ExportPage(List.of(), List.of(), List.of(), null, false));
        when(exporter.fetch(org.mockito.ArgumentMatchers.eq("memberships"), org.mockito.ArgumentMatchers.nullable(String.class), any()))
                .thenReturn(new ExportPage(List.of(), List.of(), List.of(), null, false));

        var result = new CatalogBackfillCoordinator(exporter, projection, true).runOnce();

        assertEquals(1, result.recordCount());
        var order = inOrder(exporter, projection);
        order.verify(exporter).fetch(org.mockito.ArgumentMatchers.eq("categories"), org.mockito.ArgumentMatchers.isNull(), any());
        order.verify(projection).apply(any());
        order.verify(exporter).fetch(org.mockito.ArgumentMatchers.eq("products"), org.mockito.ArgumentMatchers.isNull(), any());
        order.verify(projection).apply(any());
        order.verify(exporter).fetch(org.mockito.ArgumentMatchers.eq("memberships"), org.mockito.ArgumentMatchers.isNull(), any());
        order.verify(projection).apply(any());
        order.verify(projection).finish(result.runId(), "ofbiz", 1);
    }

    @Test
    void ordinarySynchronizationNeverDeletesStaleRows() {
        when(exporter.fetch(any(), org.mockito.ArgumentMatchers.nullable(String.class), any()))
                .thenReturn(new ExportPage(List.of(), List.of(), List.of(), null, false));
        new CatalogBackfillCoordinator(exporter, projection, false).runOnce();
        verify(projection, never()).finish(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }
}
