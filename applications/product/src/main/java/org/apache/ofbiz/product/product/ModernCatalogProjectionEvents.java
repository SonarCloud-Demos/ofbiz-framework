/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0.
 */
package org.apache.ofbiz.product.product;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.ofbiz.base.lang.JSON;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;

/** Internal anti-corruption endpoint for the Phase 3 read projection. */
public final class ModernCatalogProjectionEvents {
    private ModernCatalogProjectionEvents() { }

    public static String exportProducts(HttpServletRequest request, HttpServletResponse response)
            throws GenericEntityException, IOException {
        if (!authorized(request, response)) return "error";

        int limit = parseLimit(request.getParameter("limit"));
        Cursor after = parseCursor(request.getParameter("after"));
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        EntityCondition changed = EntityCondition.makeCondition(List.of(
                EntityCondition.makeCondition("lastUpdatedStamp", EntityOperator.GREATER_THAN,
                        Timestamp.from(after.updatedAt())),
                EntityCondition.makeCondition(List.of(
                        EntityCondition.makeCondition("lastUpdatedStamp", Timestamp.from(after.updatedAt())),
                        EntityCondition.makeCondition("productId", EntityOperator.GREATER_THAN, after.productId())),
                        EntityOperator.AND)), EntityOperator.OR);
        List<GenericValue> products = EntityQuery.use(delegator).from("Product")
                .where(changed).orderBy("lastUpdatedStamp", "productId").maxRows(limit + 1).queryList();
        boolean hasMore = products.size() > limit;
        if (hasMore) products = products.subList(0, limit);

        List<Map<String, Object>> records = new ArrayList<>();
        Cursor cursor = after;
        for (GenericValue product : products) {
            Timestamp updated = product.getTimestamp("lastUpdatedStamp");
            cursor = new Cursor(updated.toInstant(), product.getString("productId"));
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("productId", product.getString("productId"));
            record.put("internalName", product.getString("internalName"));
            record.put("productName", product.getString("productName"));
            record.put("productTypeId", product.getString("productTypeId"));
            record.put("statusId", product.getString("statusId"));
            record.put("description", product.getString("description"));
            record.put("sourceUpdatedAt", updated.toInstant().toString());
            record.put("sourceChecksum", checksum(record));
            records.add(record);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", records);
        body.put("cursor", cursor.updatedAt() + "|" + cursor.productId());
        body.put("hasMore", hasMore);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JSON.from(body).toString());
        return "success";
    }

    public static String snapshotProductIds(HttpServletRequest request, HttpServletResponse response)
            throws GenericEntityException, IOException {
        if (!authorized(request, response)) return "error";
        int limit = parseLimit(request.getParameter("limit"));
        String afterId = request.getParameter("afterId");
        if (afterId == null) afterId = "";
        Instant cutoff = parseInstant(request.getParameter("cutoff"), Instant.now());
        EntityCondition condition = EntityCondition.makeCondition(List.of(
                EntityCondition.makeCondition("productId", EntityOperator.GREATER_THAN, afterId),
                EntityCondition.makeCondition("createdStamp", EntityOperator.LESS_THAN_EQUAL_TO, Timestamp.from(cutoff))),
                EntityOperator.AND);
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        List<GenericValue> products = EntityQuery.use(delegator).from("Product").select("productId")
                .where(condition).orderBy("productId").maxRows(limit + 1).queryList();
        boolean hasMore = products.size() > limit;
        if (hasMore) products = products.subList(0, limit);
        List<String> ids = products.stream().map(value -> value.getString("productId")).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productIds", ids);
        body.put("cursor", ids.isEmpty() ? afterId : ids.get(ids.size() - 1));
        body.put("cutoff", cutoff.toString());
        body.put("hasMore", hasMore);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JSON.from(body).toString());
        return "success";
    }

    private static boolean authorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String expected = System.getenv("OFBIZ_CATALOG_EXPORT_KEY");
        String supplied = request.getHeader("X-Catalog-Export-Key");
        boolean authorized = expected != null && !expected.isBlank() && supplied != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8));
        if (!authorized) response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return authorized;
    }

    private static int parseLimit(String raw) {
        try {
            return Math.max(1, Math.min(500, Integer.parseInt(raw)));
        } catch (RuntimeException ignored) {
            return 200;
        }
    }

    private static Cursor parseCursor(String raw) {
        try {
            if (raw == null || raw.isBlank()) return new Cursor(Instant.EPOCH, "");
            int delimiter = raw.lastIndexOf('|');
            return new Cursor(Instant.parse(raw.substring(0, delimiter)), raw.substring(delimiter + 1));
        } catch (RuntimeException ignored) {
            return new Cursor(Instant.EPOCH, "");
        }
    }

    private static Instant parseInstant(String raw, Instant fallback) {
        try {
            return raw == null || raw.isBlank() ? fallback : Instant.parse(raw);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private record Cursor(Instant updatedAt, String productId) { }

    private static String checksum(Map<String, Object> record) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            record.forEach((key, value) -> {
                digest.update(key.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
