package org.apache.ofbiz.modern.catalog;

import java.util.List;
import java.util.Optional;
import org.apache.ofbiz.modern.catalog.CatalogModels.CategorySummary;
import org.apache.ofbiz.modern.catalog.CatalogModels.ProductSummary;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.CategoryRecord;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.MembershipRecord;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.ProductRecord;

interface CatalogRepository {
    Optional<CategorySummary> category(String categoryId);
    List<CategorySummary> childCategories(String categoryId);
    Optional<ProductSummary> product(String productId);
    List<ProductSummary> browse(String categoryId, int offset, int fetchSize, String sort);
    List<ProductSummary> search(String query, String categoryId, int offset, int fetchSize, String sort);
    void upsert(CategoryRecord category);
    void upsert(ProductRecord product);
    void upsert(MembershipRecord membership);
    void checkpoint(String source, String cursor);
    void recordSnapshot(String runId, String recordType, String recordId);
    long snapshotRecordCount(String runId);
    long finishSnapshot(String runId, String source, long sourceCount);
}
