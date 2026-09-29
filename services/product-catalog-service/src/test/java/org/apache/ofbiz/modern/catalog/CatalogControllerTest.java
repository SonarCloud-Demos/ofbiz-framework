package org.apache.ofbiz.modern.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.ofbiz.modern.catalog.CatalogModels.CategorySummary;
import org.apache.ofbiz.modern.catalog.CatalogModels.ProductSummary;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CatalogControllerTest {
    private final CatalogRepository repository = mock(CatalogRepository.class);
    private final CatalogController controller = new CatalogController(repository);
    private final ProductSummary gizmo = new ProductSummary("GZ-1000", "Tiny Gizmo", "Small", null,
            "FINISHED_GOOD", false, false);

    @Test
    void categoryCarriesChildrenAndValidatedCorrelation() {
        var root = new CategorySummary("CATALOG1", "Demo Browse Root", null, null);
        var child = new CategorySummary("100", "Gizmos", null, null);
        when(repository.category("CATALOG1")).thenReturn(Optional.of(root));
        when(repository.childCategories("CATALOG1")).thenReturn(List.of(child));

        var response = controller.category("CATALOG1", "test-correlation");

        assertEquals("test-correlation", response.correlationId());
        assertEquals(List.of(child), response.children());
    }

    @Test
    void browseUsesOpaqueCursorAndFetchesOneExtra() {
        var root = new CategorySummary("100", "Gizmos", null, null);
        var second = new ProductSummary("GZ-2000", "Big Gizmo", null, null, "FINISHED_GOOD", false, false);
        when(repository.category("100")).thenReturn(Optional.of(root));
        when(repository.browse("100", 0, 2, "catalog")).thenReturn(List.of(gizmo, second));

        var first = controller.browse("100", null, 1, "catalog", "browse-1");

        assertEquals(List.of(gizmo), first.items());
        assertEquals(1, CatalogController.decodeCursor(first.nextCursor()));
    }

    @Test
    void emptySearchIsSuccessfulAndHasNoCursor() {
        when(repository.search("missing", null, 0, 21, "catalog")).thenReturn(List.of());

        var response = controller.search("missing", null, null, 20, "catalog", null);

        assertEquals(List.of(), response.items());
        assertNull(response.nextCursor());
    }

    @Test
    void malformedCursorIsRejected() {
        assertThrows(ResponseStatusException.class, () -> CatalogController.decodeCursor("not@base64"));
    }
}
