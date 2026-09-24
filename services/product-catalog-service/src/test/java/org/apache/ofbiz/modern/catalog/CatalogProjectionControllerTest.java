package org.apache.ofbiz.modern.catalog;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.ProjectionBatch;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CatalogProjectionControllerTest {
    @Test
    void rejectsIncorrectIngestionKey() {
        var controller = new CatalogProjectionController(mock(CatalogProjectionService.class), "x".repeat(32));
        var batch = new ProjectionBatch("ofbiz", "cursor", null, List.of(), List.of(), List.of());
        assertThrows(ResponseStatusException.class, () -> controller.ingest("wrong", batch));
    }
}
