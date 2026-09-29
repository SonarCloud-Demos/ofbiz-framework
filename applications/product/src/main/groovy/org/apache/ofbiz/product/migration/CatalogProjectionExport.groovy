package org.apache.ofbiz.product.migration

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.time.format.DateTimeParseException
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityQuery
import org.apache.ofbiz.service.ServiceUtil

String type = recordType
if (!(type in ['categories', 'products', 'memberships'])) {
    return ServiceUtil.returnError('recordType must be categories, products, or memberships')
}

String suppliedCursor = binding.hasVariable('cursor') ? binding.getVariable('cursor') as String : null
Integer suppliedLimit = binding.hasVariable('limit') ? binding.getVariable('limit') as Integer : null
String suppliedCutoff = binding.hasVariable('cutoff') ? binding.getVariable('cutoff') as String : null
int pageSize = Math.max(1, Math.min(suppliedLimit ?: 200, 500))
Timestamp snapshotCutoff
try {
    snapshotCutoff = suppliedCutoff ? Timestamp.from(Instant.parse(suppliedCutoff)) : UtilDateTime.nowTimestamp()
} catch (DateTimeParseException invalid) {
    return ServiceUtil.returnError('cutoff must be an ISO-8601 instant')
}

List rows
switch (type) {
    case 'categories':
        rows = page('ProductCategory', 'productCategoryId', suppliedCursor, snapshotCutoff, pageSize)
        break
    case 'products':
        rows = page('Product', 'productId', suppliedCursor, snapshotCutoff, pageSize)
        break
    default:
        rows = membershipPage(suppliedCursor, snapshotCutoff, pageSize)
}

boolean more = rows.size() > pageSize
List selected = more ? rows.subList(0, pageSize) : rows
List exported = selected.collect { row -> exportRow(type, row) }
String next = more && !selected.isEmpty() ? rowCursor(type, selected.last()) : null

return [items: exported, nextCursor: next, hasMore: more, cutoff: snapshotCutoff.toInstant().toString()]

List page(String entityName, String idField, String after, Timestamp at, int size) {
    List conditions = [EntityCondition.makeCondition('lastUpdatedStamp', EntityOperator.LESS_THAN_EQUAL_TO, at)]
    if (after) {
        conditions << EntityCondition.makeCondition(idField, EntityOperator.GREATER_THAN, after)
    }
    EntityQuery query = from(entityName).where(EntityCondition.makeCondition(conditions))
    return query.orderBy(idField).maxRows(size + 1).queryList()
}

List membershipPage(String after, Timestamp at, int size) {
    List conditions = [EntityCondition.makeCondition('lastUpdatedStamp', EntityOperator.LESS_THAN_EQUAL_TO, at)]
    if (after) {
        List parts = new String(Base64.urlDecoder.decode(after), StandardCharsets.UTF_8).split('\\|', -1) as List
        if (parts.size() != 3) {
            throw new IllegalArgumentException('invalid membership cursor')
        }
        Timestamp from = Timestamp.from(Instant.parse(parts[2]))
        conditions << EntityCondition.makeCondition([
                EntityCondition.makeCondition('productCategoryId', EntityOperator.GREATER_THAN, parts[0]),
                EntityCondition.makeCondition([
                        EntityCondition.makeCondition('productCategoryId', parts[0]),
                        EntityCondition.makeCondition('productId', EntityOperator.GREATER_THAN, parts[1])
                ], EntityOperator.AND),
                EntityCondition.makeCondition([
                        EntityCondition.makeCondition('productCategoryId', parts[0]),
                        EntityCondition.makeCondition('productId', parts[1]),
                        EntityCondition.makeCondition('fromDate', EntityOperator.GREATER_THAN, from)
                ], EntityOperator.AND)
        ], EntityOperator.OR)
    }
    EntityQuery query = from('ProductCategoryMember').where(EntityCondition.makeCondition(conditions))
    return query.orderBy('productCategoryId', 'productId', 'fromDate').maxRows(size + 1).queryList()
}

Map exportRow(String kind, GenericValue row) {
    if (kind == 'categories') {
        Map value = [categoryId: row.productCategoryId, parentCategoryId: row.primaryParentCategoryId,
                name: row.categoryName ?: row.description ?: row.productCategoryId,
                description: row.longDescription ?: row.description, imageUrl: row.categoryImageUrl,
                sequenceNum: null, fromDate: null, thruDate: null]
        value.sourceChecksum = checksum(value)
        return value
    }
    if (kind == 'products') {
        Map value = [productId: row.productId, name: row.productName ?: row.internalName ?: row.productId,
                description: row.description, imageUrl: row.smallImageUrl,
                productType: row.productTypeId ?: 'UNKNOWN', virtual: row.isVirtual == 'Y', variant: row.isVariant == 'Y']
        value.sourceChecksum = checksum(value)
        return value
    }
    return [categoryId: row.productCategoryId, productId: row.productId, sequenceNum: row.sequenceNum,
            fromDate: row.fromDate.toInstant().toString(), thruDate: row.thruDate?.toInstant()?.toString()]
}

String rowCursor(String kind, GenericValue row) {
    if (kind == 'categories') {
        return row.productCategoryId
    }
    if (kind == 'products') {
        return row.productId
    }
    String raw = "${row.productCategoryId}|${row.productId}|${row.fromDate.toInstant()}"
    return Base64.urlEncoder.withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8))
}

String checksum(Map value) {
    String canonical = value.collect { key, item -> "${key}=${item == null ? '' : item}" }.join('\n')
    return MessageDigest.getInstance('SHA-256').digest(canonical.getBytes(StandardCharsets.UTF_8)).encodeHex().toString()
}
