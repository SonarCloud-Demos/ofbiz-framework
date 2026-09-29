package org.apache.ofbiz.modern.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ofbiz.modern.catalog.CatalogModels.CategorySummary;
import org.apache.ofbiz.modern.catalog.CatalogModels.ProductSummary;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.CategoryRecord;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.MembershipRecord;
import org.apache.ofbiz.modern.catalog.CatalogProjectionModels.ProductRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCatalogRepository implements CatalogRepository {
    private final JdbcClient jdbc;

    JdbcCatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CategorySummary> category(String categoryId) {
        return jdbc.sql("""
                SELECT category_id, name, description, image_url
                FROM catalog_category
                WHERE category_id = :categoryId AND (from_date IS NULL OR from_date <= :now)
                  AND (thru_date IS NULL OR thru_date > :now)
                """).param("categoryId", categoryId).param("now", OffsetDateTime.now())
                .query(JdbcCatalogRepository::category).optional();
    }

    @Override
    public List<CategorySummary> childCategories(String categoryId) {
        return jdbc.sql("""
                SELECT category_id, name, description, image_url
                FROM catalog_category
                WHERE parent_category_id = :categoryId AND (from_date IS NULL OR from_date <= :now)
                  AND (thru_date IS NULL OR thru_date > :now)
                ORDER BY sequence_num NULLS LAST, category_id
                LIMIT 100
                """).param("categoryId", categoryId).param("now", OffsetDateTime.now())
                .query(JdbcCatalogRepository::category).list();
    }

    @Override
    public Optional<ProductSummary> product(String productId) {
        return jdbc.sql("""
                SELECT product_id, name, description, image_url, product_type, is_virtual, is_variant
                FROM catalog_product WHERE product_id = :productId
                """).param("productId", productId).query(JdbcCatalogRepository::product).optional();
    }

    @Override
    public List<ProductSummary> browse(String categoryId, int offset, int fetchSize, String sort) {
        String order = "name".equals(sort) ? "LOWER(p.name), p.product_id" : "cp.sequence_num NULLS LAST, p.product_id";
        return jdbc.sql("""
                SELECT p.product_id, p.name, p.description, p.image_url, p.product_type, p.is_virtual, p.is_variant
                FROM catalog_category_product cp JOIN catalog_product p ON p.product_id = cp.product_id
                WHERE cp.category_id = :categoryId
                  AND (cp.from_date IS NULL OR cp.from_date <= :now)
                  AND (cp.thru_date IS NULL OR cp.thru_date > :now)
                ORDER BY
                """ + order + " LIMIT :fetchSize OFFSET :offset")
                .param("categoryId", categoryId).param("offset", offset)
                .param("now", OffsetDateTime.now()).param("fetchSize", fetchSize)
                .query(JdbcCatalogRepository::product).list();
    }

    @Override
    public List<ProductSummary> search(
            String query, String categoryId, int offset, int fetchSize, String sort) {
        String categoryClause = categoryId == null ? "" : """
                 AND EXISTS (SELECT 1 FROM catalog_category_product cp
                             WHERE cp.product_id = p.product_id AND cp.category_id = :categoryId
                               AND (cp.from_date IS NULL OR cp.from_date <= :now)
                               AND (cp.thru_date IS NULL OR cp.thru_date > :now))
                """;
        String order = "name".equals(sort) ? "LOWER(p.name), p.product_id" : "p.product_id";
        var statement = jdbc.sql("""
                SELECT p.product_id, p.name, p.description, p.image_url, p.product_type, p.is_virtual, p.is_variant
                FROM catalog_product p
                WHERE (LOWER(p.name) LIKE :query OR LOWER(COALESCE(p.description, '')) LIKE :query)
                """ + categoryClause + " ORDER BY " + order + " LIMIT :fetchSize OFFSET :offset")
                .param("query", "%" + query.toLowerCase() + "%")
                .param("fetchSize", fetchSize).param("offset", offset);
        if (categoryId != null) statement = statement.param("categoryId", categoryId).param("now", OffsetDateTime.now());
        return statement.query(JdbcCatalogRepository::product).list();
    }

    @Override
    public void upsert(CategoryRecord value) {
        jdbc.sql("""
                INSERT INTO catalog_category
                    (category_id, parent_category_id, name, description, image_url, sequence_num,
                     from_date, thru_date, source_checksum)
                VALUES (:id, :parent, :name, :description, :image, :sequence, :fromDate, :thruDate, :checksum)
                ON CONFLICT (category_id) DO UPDATE SET
                    parent_category_id = EXCLUDED.parent_category_id, name = EXCLUDED.name,
                    description = EXCLUDED.description, image_url = EXCLUDED.image_url,
                    sequence_num = EXCLUDED.sequence_num, from_date = EXCLUDED.from_date,
                    thru_date = EXCLUDED.thru_date, source_checksum = EXCLUDED.source_checksum,
                    projected_at = CURRENT_TIMESTAMP
                WHERE catalog_category.source_checksum <> EXCLUDED.source_checksum
                """).param("id", value.categoryId()).param("parent", value.parentCategoryId())
                .param("name", value.name()).param("description", value.description())
                .param("image", value.imageUrl()).param("sequence", value.sequenceNum())
                .param("fromDate", timestamp(value.fromDate())).param("thruDate", timestamp(value.thruDate()))
                .param("checksum", value.sourceChecksum()).update();
    }

    @Override
    public void upsert(ProductRecord value) {
        jdbc.sql("""
                INSERT INTO catalog_product
                    (product_id, name, description, image_url, product_type, is_virtual, is_variant, source_checksum)
                VALUES (:id, :name, :description, :image, :type, :virtual, :variant, :checksum)
                ON CONFLICT (product_id) DO UPDATE SET
                    name = EXCLUDED.name, description = EXCLUDED.description, image_url = EXCLUDED.image_url,
                    product_type = EXCLUDED.product_type, is_virtual = EXCLUDED.is_virtual,
                    is_variant = EXCLUDED.is_variant, source_checksum = EXCLUDED.source_checksum,
                    projected_at = CURRENT_TIMESTAMP
                WHERE catalog_product.source_checksum <> EXCLUDED.source_checksum
                """).param("id", value.productId()).param("name", value.name())
                .param("description", value.description()).param("image", value.imageUrl())
                .param("type", value.productType()).param("virtual", value.virtual())
                .param("variant", value.variant()).param("checksum", value.sourceChecksum()).update();
    }

    @Override
    public void upsert(MembershipRecord value) {
        jdbc.sql("""
                INSERT INTO catalog_category_product
                    (category_id, product_id, sequence_num, from_date, thru_date)
                VALUES (:categoryId, :productId, :sequence, :fromDate, :thruDate)
                ON CONFLICT (category_id, product_id, from_date) DO UPDATE SET
                    sequence_num = EXCLUDED.sequence_num, thru_date = EXCLUDED.thru_date
                """).param("categoryId", value.categoryId()).param("productId", value.productId())
                .param("sequence", value.sequenceNum()).param("fromDate", timestamp(value.fromDate()))
                .param("thruDate", timestamp(value.thruDate())).update();
    }

    @Override
    public void checkpoint(String source, String cursor) {
        jdbc.sql("""
                INSERT INTO catalog_projection_checkpoint (source_name, source_cursor)
                VALUES (:source, :cursor)
                ON CONFLICT (source_name) DO UPDATE SET
                    source_cursor = EXCLUDED.source_cursor, updated_at = CURRENT_TIMESTAMP
                """).param("source", source).param("cursor", cursor).update();
    }

    @Override
    public void recordSnapshot(String runId, String recordType, String recordId) {
        jdbc.sql("""
                INSERT INTO catalog_snapshot_seen (run_id, record_type, record_id)
                VALUES (:runId, :recordType, :recordId) ON CONFLICT DO NOTHING
                """).param("runId", runId).param("recordType", recordType).param("recordId", recordId).update();
    }

    @Override
    public long snapshotRecordCount(String runId) {
        Long count = jdbc.sql("SELECT COUNT(*) FROM catalog_snapshot_seen WHERE run_id = :runId")
                .param("runId", runId).query(Long.class).single();
        return count;
    }

    @Override
    public long finishSnapshot(String runId, String source, long sourceCount) {
        long deleted = 0;
        deleted += jdbc.sql("""
                DELETE FROM catalog_category_product cp
                WHERE NOT EXISTS (SELECT 1 FROM catalog_snapshot_seen seen
                    WHERE seen.run_id = :runId AND seen.record_type = 'membership'
                      AND seen.record_id = CONCAT(cp.category_id, '|', cp.product_id, '|',
                          (EXTRACT(EPOCH FROM cp.from_date) * 1000)::BIGINT))
                """).param("runId", runId).update();
        deleted += jdbc.sql("""
                DELETE FROM catalog_product p
                WHERE NOT EXISTS (SELECT 1 FROM catalog_snapshot_seen seen
                    WHERE seen.run_id = :runId AND seen.record_type = 'product' AND seen.record_id = p.product_id)
                """).param("runId", runId).update();
        deleted += jdbc.sql("""
                DELETE FROM catalog_category c
                WHERE NOT EXISTS (SELECT 1 FROM catalog_snapshot_seen seen
                    WHERE seen.run_id = :runId AND seen.record_type = 'category' AND seen.record_id = c.category_id)
                """).param("runId", runId).update();
        jdbc.sql("""
                INSERT INTO catalog_reconciliation_run (run_id, source_name, source_count, deleted_count)
                VALUES (:runId, :source, :sourceCount, :deleted)
                """).param("runId", runId).param("source", source).param("sourceCount", sourceCount)
                .param("deleted", deleted).update();
        jdbc.sql("DELETE FROM catalog_snapshot_seen WHERE run_id = :runId").param("runId", runId).update();
        return deleted;
    }

    private static CategorySummary category(ResultSet rs, int row) throws SQLException {
        return new CategorySummary(rs.getString("category_id"), rs.getString("name"),
                rs.getString("description"), rs.getString("image_url"));
    }

    private static ProductSummary product(ResultSet rs, int row) throws SQLException {
        return new ProductSummary(rs.getString("product_id"), rs.getString("name"),
                rs.getString("description"), rs.getString("image_url"), rs.getString("product_type"),
                rs.getBoolean("is_virtual"), rs.getBoolean("is_variant"));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
