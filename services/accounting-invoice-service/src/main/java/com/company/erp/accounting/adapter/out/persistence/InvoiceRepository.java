package com.company.erp.accounting.adapter.out.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class InvoiceRepository {
    private static final String SOURCE = "legacy-ofbiz-postgres";
    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcTemplate plainJdbc;

    public InvoiceRepository(NamedParameterJdbcTemplate jdbc, JdbcTemplate plainJdbc) {
        this.jdbc = jdbc;
        this.plainJdbc = plainJdbc;
    }

    public Map<String, Object> find(Map<String, String> filters, int limit, int offset) {
        var where = new StringBuilder(" where 1=1");
        var parameters = new MapSqlParameterSource().addValue("limit", limit).addValue("offset", offset);
        addFilter(where, parameters, filters, "invoiceId", "i.invoice_id");
        addFilter(where, parameters, filters, "invoiceTypeId", "i.invoice_type_id");
        addFilter(where, parameters, filters, "statusId", "i.status_id");
        addFilter(where, parameters, filters, "partyIdFrom", "i.party_id_from");
        addFilter(where, parameters, filters, "partyId", "i.party_id");

        String aggregates = """
                from invoice i
                left join invoice_type it on it.invoice_type_id=i.invoice_type_id
                left join status_item si on si.status_id=i.status_id
                left join (select invoice_id, count(*) item_count,
                    coalesce(sum(coalesce(quantity,1)*coalesce(amount,0)),0) invoice_total
                    from invoice_item group by invoice_id) items on items.invoice_id=i.invoice_id
                left join (select invoice_id, coalesce(sum(amount_applied),0) applied_total
                    from payment_application where invoice_id is not null group by invoice_id) payments
                    on payments.invoice_id=i.invoice_id
                """;
        String select = """
                select i.invoice_id, i.invoice_type_id, it.description type_description,
                    i.status_id, si.description status_description, i.party_id_from, i.party_id,
                    i.invoice_date, i.due_date, i.paid_date, i.currency_uom_id,
                    coalesce(items.item_count,0) item_count, coalesce(items.invoice_total,0) invoice_total,
                    coalesce(payments.applied_total,0) applied_total,
                    coalesce(items.invoice_total,0)-coalesce(payments.applied_total,0) outstanding_total
                """;
        long total = jdbc.queryForObject("select count(*) " + aggregates + where, parameters, Long.class);
        List<Map<String, Object>> invoices = jdbc.queryForList(
                select + aggregates + where + " order by i.invoice_date desc nulls last, i.invoice_id limit :limit offset :offset",
                parameters);
        return Map.of("invoices", invoices, "pagination", Map.of("limit", limit, "offset", offset, "total", total),
                "source", SOURCE, "notes", totalsNote());
    }

    public Optional<Map<String, Object>> detail(String invoiceId) {
        var rows = jdbc.queryForList("""
                select i.*, it.description type_description, si.description status_description
                from invoice i left join invoice_type it on it.invoice_type_id=i.invoice_type_id
                left join status_item si on si.status_id=i.status_id where i.invoice_id=:invoiceId
                """, Map.of("invoiceId", invoiceId));
        if (rows.isEmpty()) return Optional.empty();
        var result = new LinkedHashMap<String, Object>();
        result.put("header", rows.get(0));
        result.put("lineItems", jdbc.queryForList("""
                select invoice_item_seq_id, invoice_item_type_id, product_id, description, quantity, amount,
                    coalesce(quantity,1)*coalesce(amount,0) line_total
                from invoice_item where invoice_id=:invoiceId order by invoice_item_seq_id
                """, Map.of("invoiceId", invoiceId)));
        result.put("paymentApplications", jdbc.queryForList("""
                select payment_application_id, payment_id, invoice_item_seq_id, amount_applied
                from payment_application where invoice_id=:invoiceId order by payment_application_id
                """, Map.of("invoiceId", invoiceId)));
        result.put("statusHistory", jdbc.queryForList("""
                select s.status_id, si.description status_description, s.status_date, s.change_by_user_login_id
                from invoice_status s left join status_item si on si.status_id=s.status_id
                where s.invoice_id=:invoiceId order by s.status_date
                """, Map.of("invoiceId", invoiceId)));
        result.put("source", SOURCE);
        result.put("notes", totalsNote());
        return Optional.of(result);
    }

    @Transactional
    public Map<String, Object> create(InvoiceHeader input) {
        try {
            plainJdbc.update("""
                    insert into invoice (invoice_id, invoice_type_id, party_id_from, party_id, role_type_id,
                    status_id, billing_account_id, contact_mech_id, invoice_date, due_date, paid_date,
                    invoice_message, reference_number, description, currency_uom_id, recurrence_info_id,
                    last_updated_stamp, last_updated_tx_stamp, created_stamp, created_tx_stamp)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,current_timestamp,current_timestamp,current_timestamp,current_timestamp)
                    """, input.invoiceId(), input.invoiceTypeId(), input.partyIdFrom(), input.partyId(), input.roleTypeId(),
                    input.statusId(), input.billingAccountId(), input.contactMechId(), timestamp(input.invoiceDate()),
                    timestamp(input.dueDate()), timestamp(input.paidDate()), input.invoiceMessage(), input.referenceNumber(),
                    input.description(), input.currencyUomId(), input.recurrenceInfoId());
        } catch (DuplicateKeyException ex) {
            throw new InvoiceConflictException("Invoice already exists: " + input.invoiceId());
        }
        return detail(input.invoiceId()).orElseThrow();
    }

    @Transactional
    public Optional<Map<String, Object>> update(String invoiceId, InvoiceHeader input) {
        int changed = plainJdbc.update("""
                update invoice set invoice_type_id=?, party_id_from=?, party_id=?, role_type_id=?, status_id=?,
                billing_account_id=?, contact_mech_id=?, invoice_date=?, due_date=?, paid_date=?, invoice_message=?,
                reference_number=?, description=?, currency_uom_id=?, recurrence_info_id=?, last_updated_stamp=current_timestamp,
                last_updated_tx_stamp=current_timestamp where invoice_id=?
                """, input.invoiceTypeId(), input.partyIdFrom(), input.partyId(), input.roleTypeId(), input.statusId(),
                input.billingAccountId(), input.contactMechId(), timestamp(input.invoiceDate()), timestamp(input.dueDate()),
                timestamp(input.paidDate()), input.invoiceMessage(), input.referenceNumber(), input.description(),
                input.currencyUomId(), input.recurrenceInfoId(), invoiceId);
        return changed == 0 ? Optional.empty() : detail(invoiceId);
    }

    @Transactional
    public boolean delete(String invoiceId) {
        Integer dependencies = plainJdbc.queryForObject("""
                select (select count(*) from invoice_item where invoice_id=?)
                    + (select count(*) from payment_application where invoice_id=?)
                """, Integer.class, invoiceId, invoiceId);
        if (dependencies != null && dependencies > 0) {
            throw new InvoiceConflictException("Invoice has line items or payment applications and cannot be deleted");
        }
        plainJdbc.update("delete from invoice_status where invoice_id=?", invoiceId);
        return plainJdbc.update("delete from invoice where invoice_id=?", invoiceId) == 1;
    }

    private static void addFilter(StringBuilder where, MapSqlParameterSource parameters,
            Map<String, String> filters, String parameter, String column) {
        String value = filters.get(parameter);
        if (value != null && !value.isBlank()) {
            where.append(" and ").append(column).append(" = :").append(parameter);
            parameters.addValue(parameter, value.trim());
        }
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static String totalsNote() {
        return "Totals are SQL-derived projections. Reconcile with OFBiz InvoiceWorker, service ECAs, and GL posting rules before write cutover.";
    }

    public record InvoiceHeader(String invoiceId, String invoiceTypeId, String partyIdFrom, String partyId,
            String roleTypeId, String statusId, String billingAccountId, String contactMechId, Instant invoiceDate,
            Instant dueDate, Instant paidDate, String invoiceMessage, String referenceNumber, String description,
            String currencyUomId, String recurrenceInfoId) {}

    public static class InvoiceConflictException extends RuntimeException {
        public InvoiceConflictException(String message) { super(message); }
    }
}
