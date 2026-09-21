package com.company.erp.accounting.adapter.out.persistence;

import static com.company.erp.accounting.domain.InvoiceModels.*;

import com.company.erp.accounting.application.port.out.InvoiceRepositoryPort;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class JdbcInvoiceRepository implements InvoiceRepositoryPort {
    public static final String SOURCE = "legacy-ofbiz-postgres";
    public static final String TOTALS_NOTE = "Totals are SQL-derived from quantity * amount and payment applications; reconcile with OFBiz InvoiceWorker, service ECAs, and GL posting rules before write cutover.";
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcInvoiceRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Page search(Search search) {
        var where = new StringBuilder(" WHERE 1=1");
        var params = new MapSqlParameterSource();
        filter(where, params, "invoiceId", search.invoiceId());
        filter(where, params, "invoiceTypeId", search.invoiceTypeId());
        filter(where, params, "statusId", search.statusId());
        filter(where, params, "partyIdFrom", search.partyIdFrom());
        filter(where, params, "partyId", search.partyId());
        params.addValue("limit", search.limit()).addValue("offset", search.offset());
        String joins = " FROM invoice i LEFT JOIN invoice_type it ON it.invoice_type_id=i.invoice_type_id "
                + "LEFT JOIN status_item si ON si.status_id=i.status_id ";
        long total = jdbc.queryForObject("SELECT count(*)" + joins + where, params, Long.class);
        String sql = "SELECT i.*, it.description type_description, si.description status_description, "
                + "(SELECT count(*) FROM invoice_item x WHERE x.invoice_id=i.invoice_id) item_count, "
                + "COALESCE((SELECT sum(COALESCE(x.quantity,1)*COALESCE(x.amount,0)) FROM invoice_item x WHERE x.invoice_id=i.invoice_id),0) invoice_total, "
                + "COALESCE((SELECT sum(COALESCE(p.amount_applied,0)) FROM payment_application p WHERE p.invoice_id=i.invoice_id),0) payment_total "
                + joins + where + " ORDER BY i.invoice_date DESC NULLS LAST, i.invoice_id LIMIT :limit OFFSET :offset";
        return new Page(jdbc.query(sql, params, (rs, n) -> summary(rs)), search.limit(), search.offset(), total, SOURCE, TOTALS_NOTE);
    }

    @Override public Detail find(String invoiceId) {
        var params = Map.of("invoiceId", invoiceId);
        String sql = "SELECT i.*, it.description type_description, si.description status_description, "
                + "(SELECT count(*) FROM invoice_item x WHERE x.invoice_id=i.invoice_id) item_count, "
                + "COALESCE((SELECT sum(COALESCE(x.quantity,1)*COALESCE(x.amount,0)) FROM invoice_item x WHERE x.invoice_id=i.invoice_id),0) invoice_total, "
                + "COALESCE((SELECT sum(COALESCE(p.amount_applied,0)) FROM payment_application p WHERE p.invoice_id=i.invoice_id),0) payment_total "
                + "FROM invoice i LEFT JOIN invoice_type it ON it.invoice_type_id=i.invoice_type_id "
                + "LEFT JOIN status_item si ON si.status_id=i.status_id WHERE i.invoice_id=:invoiceId";
        List<Map<String, Object>> headers = jdbc.query(sql, params, (rs, n) -> {
            Map<String, Object> value = new HashMap<>(); value.put("summary", summary(rs));
            value.put("description", rs.getString("description")); value.put("reference", rs.getString("reference_number")); return value;
        });
        if (headers.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found: " + invoiceId);
        var header = headers.get(0);
        List<Item> items = jdbc.query("SELECT invoice_item_seq_id, invoice_item_type_id, product_id, description, quantity, amount FROM invoice_item WHERE invoice_id=:invoiceId ORDER BY invoice_item_seq_id", params,
                (rs, n) -> { BigDecimal quantity = decimal(rs, "quantity"); BigDecimal amount = decimal(rs, "amount"); return new Item(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), quantity, amount, nz(quantity, BigDecimal.ONE).multiply(nz(amount, BigDecimal.ZERO))); });
        List<PaymentApplication> payments = jdbc.query("SELECT payment_application_id,payment_id,invoice_item_seq_id,amount_applied FROM payment_application WHERE invoice_id=:invoiceId ORDER BY payment_application_id", params,
                (rs, n) -> new PaymentApplication(rs.getString(1), rs.getString(2), rs.getString(3), decimal(rs, "amount_applied")));
        List<StatusHistory> history = jdbc.query("SELECT s.status_id,si.description,s.status_date,s.change_by_user_login_id FROM invoice_status s LEFT JOIN status_item si ON si.status_id=s.status_id WHERE s.invoice_id=:invoiceId ORDER BY s.status_date", params,
                (rs, n) -> new StatusHistory(rs.getString(1), rs.getString(2), instant(rs, "status_date"), rs.getString(4)));
        return new Detail((Summary) header.get("summary"), (String) header.get("description"), (String) header.get("reference"), items, payments, history, SOURCE, TOTALS_NOTE);
    }

    @Override public void insert(HeaderCommand c) {
        String sql = "INSERT INTO invoice (invoice_id,invoice_type_id,party_id_from,party_id,role_type_id,status_id,invoice_date,due_date,description,reference_number,currency_uom_id,last_updated_stamp,last_updated_tx_stamp,created_stamp,created_tx_stamp) "
                + "VALUES (:invoiceId,:invoiceTypeId,:partyIdFrom,:partyId,:roleTypeId,:statusId,:invoiceDate,:dueDate,:description,:referenceNumber,:currencyUomId,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)";
        try { jdbc.update(sql, command(c)); } catch (DuplicateKeyException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice already exists: " + c.invoiceId(), e); }
    }

    @Override public void update(String id, HeaderCommand c) {
        var p = command(c).addValue("targetId", id);
        int changed = jdbc.update("UPDATE invoice SET invoice_type_id=:invoiceTypeId,party_id_from=:partyIdFrom,party_id=:partyId,role_type_id=:roleTypeId,status_id=:statusId,invoice_date=:invoiceDate,due_date=:dueDate,description=:description,reference_number=:referenceNumber,currency_uom_id=:currencyUomId,last_updated_stamp=CURRENT_TIMESTAMP,last_updated_tx_stamp=CURRENT_TIMESTAMP WHERE invoice_id=:targetId", p);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found: " + id);
    }

    @Override public void deleteGuarded(String id) {
        var p = Map.of("id", id);
        Long dependencies = jdbc.queryForObject("SELECT (SELECT count(*) FROM invoice_item WHERE invoice_id=:id)+(SELECT count(*) FROM payment_application WHERE invoice_id=:id)", p, Long.class);
        if (dependencies != null && dependencies > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice has line items or payment applications and cannot be deleted");
        int changed = jdbc.update("DELETE FROM invoice WHERE invoice_id=:id", p);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found: " + id);
    }

    private static void filter(StringBuilder sql, MapSqlParameterSource p, String field, String value) {
        if (value != null && !value.isBlank()) { sql.append(" AND i.").append(toColumn(field)).append("=:").append(field); p.addValue(field, value); }
    }
    private static String toColumn(String field) { return field.replaceAll("([A-Z])", "_$1").toLowerCase(); }
    private static MapSqlParameterSource command(HeaderCommand c) { return new MapSqlParameterSource()
            .addValue("invoiceId", c.invoiceId()).addValue("invoiceTypeId", c.invoiceTypeId()).addValue("partyIdFrom", c.partyIdFrom())
            .addValue("partyId", c.partyId()).addValue("roleTypeId", c.roleTypeId()).addValue("statusId", c.statusId())
            .addValue("invoiceDate", timestamp(c.invoiceDate())).addValue("dueDate", timestamp(c.dueDate())).addValue("description", c.description())
            .addValue("referenceNumber", c.referenceNumber()).addValue("currencyUomId", c.currencyUomId()); }
    private static Summary summary(ResultSet rs) throws SQLException { BigDecimal total=decimal(rs,"invoice_total"), paid=decimal(rs,"payment_total"); return new Summary(rs.getString("invoice_id"),rs.getString("invoice_type_id"),rs.getString("type_description"),rs.getString("status_id"),rs.getString("status_description"),rs.getString("party_id_from"),rs.getString("party_id"),instant(rs,"invoice_date"),instant(rs,"due_date"),rs.getString("currency_uom_id"),rs.getLong("item_count"),total,paid,nz(total,BigDecimal.ZERO).subtract(nz(paid,BigDecimal.ZERO))); }
    private static BigDecimal decimal(ResultSet rs,String name) throws SQLException { return rs.getBigDecimal(name); }
    private static BigDecimal nz(BigDecimal value,BigDecimal fallback) { return value == null ? fallback : value; }
    private static Instant instant(ResultSet rs,String name) throws SQLException { Timestamp t=rs.getTimestamp(name); return t == null ? null : t.toInstant(); }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
}
