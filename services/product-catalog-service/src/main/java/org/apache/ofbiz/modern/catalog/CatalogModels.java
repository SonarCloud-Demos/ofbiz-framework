package org.apache.ofbiz.modern.catalog;

import java.util.List;

final class CatalogModels {
    private CatalogModels() { }

    record CategorySummary(String categoryId, String name, String description, String imageUrl) { }

    record CategoryDetail(
            String categoryId,
            String name,
            String description,
            String imageUrl,
            List<CategorySummary> children,
            String correlationId) { }

    record ProductSummary(
            String productId,
            String name,
            String description,
            String imageUrl,
            String productType,
            boolean virtual,
            boolean variant) { }

    record ProductPage(List<ProductSummary> items, String nextCursor, String correlationId) { }
}
