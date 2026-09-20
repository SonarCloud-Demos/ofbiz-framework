package com.company.erp.accounting.adapter.in.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.company.erp.accounting.adapter.out.persistence.InvoiceRepository;
import com.company.erp.accounting.adapter.out.persistence.InvoiceRepository.InvoiceConflictException;
import com.company.erp.accounting.adapter.out.persistence.InvoiceRepository.InvoiceHeader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvoiceController {
    private final InvoiceRepository invoices;

    public InvoiceController(InvoiceRepository invoices) { this.invoices = invoices; }

    @GetMapping("/api/accounting/invoices/health")
    public Map<String, String> health() { return Map.of("status", "UP", "service", "modern-accounting-invoice-service"); }

    @GetMapping("/api/accounting/invoices")
    public Map<String, Object> list(@RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset, @RequestParam Map<String, String> filters) {
        return invoices.find(filters, Math.min(Math.max(limit, 1), 100), Math.max(offset, 0));
    }

    @GetMapping("/api/accounting/invoices/{invoiceId}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable String invoiceId) {
        return invoices.detail(invoiceId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/accounting/invoices")
    public ResponseEntity<Map<String, Object>> create(@RequestBody InvoiceHeader input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invoices.create(input));
    }

    @PutMapping("/api/accounting/invoices/{invoiceId}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String invoiceId, @RequestBody InvoiceHeader input) {
        return invoices.update(invoiceId, input).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/accounting/invoices/{invoiceId}")
    public ResponseEntity<Void> delete(@PathVariable String invoiceId) {
        return invoices.delete(invoiceId) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping(value = {"/modern/accounting/invoices", "/accounting/control/findInvoices"}, produces = MediaType.TEXT_HTML_VALUE)
    public String ui(@RequestParam Map<String, String> filters,
            @RequestParam(defaultValue = "25") int limit, @RequestParam(defaultValue = "0") int offset) {
        Map<String, Object> model = invoices.find(filters, Math.min(Math.max(limit, 1), 100), Math.max(offset, 0));
        @SuppressWarnings("unchecked")
        var rows = (Iterable<Map<String, Object>>) model.get("invoices");
        StringBuilder body = new StringBuilder();
        for (var row : rows) {
            String id = escape(row.get("invoice_id"));
            body.append("<tr><td><a href=\"/api/accounting/invoices/").append(url(id)).append("\">").append(id)
                    .append("</a></td><td>").append(escape(row.get("type_description"))).append("</td><td>")
                    .append(escape(row.get("status_description"))).append("</td><td>")
                    .append(escape(row.get("party_id_from"))).append(" → ").append(escape(row.get("party_id")))
                    .append("</td><td>").append(escape(row.get("invoice_total"))).append(" ")
                    .append(escape(row.get("currency_uom_id"))).append("</td><td><a href=\"/accounting/control/viewInvoice?invoiceId=")
                    .append(url(id)).append("\">Legacy detail</a></td></tr>");
        }
        return page(filters, body.toString());
    }

    @ExceptionHandler(InvoiceConflictException.class)
    public ResponseEntity<Map<String, String>> conflict(InvoiceConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    private static String page(Map<String, String> filters, String rows) {
        return """
                <!doctype html><html><head><meta charset="utf-8"><title>OFBiz Accounting Invoices</title>
                <style>body{margin:0;font:14px Arial;color:#133d3b;background:#f7fbfb}header{background:#1BC5BD;padding:15px 24px;display:flex;align-items:center;gap:22px}header b{font-size:24px}nav{background:#133d3b;padding:10px 24px}nav a{color:white;margin-right:17px}main{padding:24px}.panel{background:white;border-top:5px solid #1BC5BD;padding:18px;box-shadow:0 2px 8px #ccd}label{margin-right:12px}input{background:#dcfffd;border:1px solid #1BC5BD;padding:6px}button{background:#1BC5BD;border:0;padding:8px 18px}table{border-collapse:collapse;width:100%;margin-top:20px}th,td{padding:9px;border-bottom:1px solid #dcfffd;text-align:left}.note{background:#dcfffd;padding:10px}</style></head><body>
                <header><img src="/helveticus/images/ofbiz_logo_white.svg" alt="OFBiz logo" height="34"><b>OFBiz</b><span>Accounting / Modern Invoice Search</span></header>
                <nav><a href="/webtools">Webtools</a><a href="/ordermgr">Order Manager</a><a href="/partymgr">Party Manager</a><a href="/catalog">Product Catalog</a><a href="/accounting/control/findPayments">Payments</a><a href="/accounting/control/findPaymentGroup">Payment Groups</a><a href="/accounting/control/FindAcctgTrans">Transactions</a><a href="/accounting/control/findBillingAccount">Billing Accounts</a><a href="/accounting/control/FindFinAccount">Financial Accounts</a><a href="/accounting/control/TaxAuthorityMain">Tax Authorities</a><a href="/accounting/control/FindAgreement">Agreements</a><a href="/accounting/control/FindFixedAsset">Fixed Assets</a><a href="/accounting/control/FindBudget">Budgets</a><a href="/accounting/control/GlobalGLSettings">GL Settings</a><a href="/accounting/control/FindOrganization">Companies</a></nav>
                <main><div class="panel"><h1>Invoices</h1><form method="get" action="/accounting/control/findInvoices">
                """ + field("invoiceId", filters) + field("invoiceTypeId", filters) + field("statusId", filters)
                + field("partyIdFrom", filters) + field("partyId", filters) + """
                <button type="submit">Search modern invoices</button></form><p class="note">SQL-derived totals are a migration projection and must be reconciled with OFBiz InvoiceWorker before write cutover.</p>
                <table><thead><tr><th>Invoice</th><th>Type</th><th>Status</th><th>Parties</th><th>Total</th><th>Legacy</th></tr></thead><tbody>"""
                + rows + "</tbody></table></div></main></body></html>";
    }

    private static String field(String name, Map<String, String> values) {
        return "<label>" + name + " <input name=\"" + name + "\" value=\"" + escape(values.get(name)) + "\"></label>";
    }
    private static String escape(Object value) {
        if (value == null) return "";
        return value.toString().replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
    private static String url(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
