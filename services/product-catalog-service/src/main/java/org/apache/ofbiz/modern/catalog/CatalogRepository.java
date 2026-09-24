/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0.
 */
package org.apache.ofbiz.modern.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CatalogRepository {
    private final JdbcClient jdbc;
    CatalogRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    List<CatalogProduct> search(String query, String type, String status, int limit, int offset) {
        String term = query == null ? "" : query.strip().toLowerCase();
        return jdbc.sql("""
                SELECT * FROM catalog_product
                WHERE (:term = '' OR LOWER(product_id) LIKE :pattern OR LOWER(COALESCE(product_name, '')) LIKE :pattern
                  OR LOWER(COALESCE(internal_name, '')) LIKE :pattern)
                  AND (:type = '' OR product_type_id = :type)
                  AND (:status = '' OR status_id = :status)
                ORDER BY product_id LIMIT :limit OFFSET :offset
                """).param("term", term).param("pattern", "%" + term + "%")
                .param("type", type == null ? "" : type).param("status", status == null ? "" : status)
                .param("limit", limit).param("offset", offset).query(CatalogRepository::map).list();
    }

    CatalogProduct find(String id) {
        return jdbc.sql("SELECT * FROM catalog_product WHERE product_id = :id")
                .param("id", id).query(CatalogRepository::map).optional().orElse(null);
    }

    void upsert(CatalogProduct product) {
        jdbc.sql("""
                INSERT INTO catalog_product(product_id, internal_name, product_name, product_type_id, status_id,
                  description, source_updated_at, source_checksum)
                VALUES (:id, :internal, :name, :type, :status, :description, :updated, :checksum)
                ON CONFLICT (product_id) DO UPDATE SET internal_name=EXCLUDED.internal_name,
                  product_name=EXCLUDED.product_name, product_type_id=EXCLUDED.product_type_id,
                  status_id=EXCLUDED.status_id, description=EXCLUDED.description,
                  source_updated_at=EXCLUDED.source_updated_at, source_checksum=EXCLUDED.source_checksum,
                  projected_at=CURRENT_TIMESTAMP
                WHERE catalog_product.source_updated_at <= EXCLUDED.source_updated_at
                """).param("id", product.productId()).param("internal", product.internalName())
                .param("name", product.productName()).param("type", product.productTypeId())
                .param("status", product.statusId()).param("description", product.description())
                .param("updated", java.sql.Timestamp.from(product.sourceUpdatedAt()))
                .param("checksum", product.sourceChecksum()).update();
    }

    long count() { return jdbc.sql("SELECT COUNT(*) FROM catalog_product").query(Long.class).single(); }

    void checkpoint(String cursor) {
        jdbc.sql("""
                INSERT INTO catalog_projection_checkpoint(source_name, source_cursor)
                VALUES ('ofbiz-product', :cursor)
                ON CONFLICT (source_name) DO UPDATE SET source_cursor=EXCLUDED.source_cursor,
                  updated_at=CURRENT_TIMESTAMP
                """).param("cursor", cursor).update();
    }

    String checkpoint() {
        return jdbc.sql("SELECT source_cursor FROM catalog_projection_checkpoint WHERE source_name='ofbiz-product'")
                .query(String.class).optional().orElse("not-started");
    }

    void recordSnapshotIds(String runId, List<String> productIds) {
        productIds.forEach(productId -> jdbc.sql("""
                INSERT INTO catalog_snapshot_seen(run_id, product_id) VALUES (:run, :id)
                ON CONFLICT DO NOTHING
                """).param("run", runId).param("id", productId).update());
    }

    long finishSnapshot(String runId, Instant cutoff, long sourceCount) {
        int deleted = jdbc.sql("""
                DELETE FROM catalog_product p WHERE p.source_updated_at <= :cutoff
                  AND NOT EXISTS (SELECT 1 FROM catalog_snapshot_seen s
                    WHERE s.run_id = :run AND s.product_id = p.product_id)
                """).param("cutoff", java.sql.Timestamp.from(cutoff)).param("run", runId).update();
        jdbc.sql("""
                INSERT INTO catalog_reconciliation_run(run_id, cutoff_at, source_count, deleted_count)
                VALUES (:run, :cutoff, :sourceCount, :deleted)
                """).param("run", runId).param("cutoff", java.sql.Timestamp.from(cutoff))
                .param("sourceCount", sourceCount).param("deleted", deleted).update();
        jdbc.sql("DELETE FROM catalog_snapshot_seen WHERE run_id = :run").param("run", runId).update();
        return deleted;
    }

    Map<String, Object> latestReconciliation() {
        return jdbc.sql("""
                SELECT run_id, cutoff_at, source_count, deleted_count, completed_at
                FROM catalog_reconciliation_run ORDER BY completed_at DESC LIMIT 1
                """).query((rs, row) -> Map.<String, Object>of(
                        "runId", rs.getString("run_id"), "cutoff", rs.getTimestamp("cutoff_at").toInstant(),
                        "sourceCount", rs.getLong("source_count"), "deletedCount", rs.getLong("deleted_count"),
                        "completedAt", rs.getTimestamp("completed_at").toInstant()))
                .optional().orElse(Map.of("status", "not-run"));
    }

    private static CatalogProduct map(ResultSet rs, int row) throws SQLException {
        return new CatalogProduct(rs.getString("product_id"), rs.getString("internal_name"),
                rs.getString("product_name"), rs.getString("product_type_id"), rs.getString("status_id"),
                rs.getString("description"), rs.getTimestamp("source_updated_at").toInstant(),
                rs.getString("source_checksum"));
    }
}
