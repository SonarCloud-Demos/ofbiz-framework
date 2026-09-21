package com.company.erp.accounting.adapter.in.web;

import com.company.erp.accounting.application.port.in.InvoiceUseCase;
import com.company.erp.accounting.domain.InvoiceModels.Search;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvoiceUiController {
    private final InvoiceUseCase invoices;
    public InvoiceUiController(InvoiceUseCase invoices) { this.invoices = invoices; }

    @GetMapping(value={"/modern/accounting/invoices", "/accounting/control/findInvoices"}, produces=MediaType.TEXT_HTML_VALUE)
    public String page(@RequestParam(defaultValue="20") int limit, @RequestParam(required=false) String invoiceId,
            @RequestParam(required=false) String invoiceTypeId, @RequestParam(required=false) String statusId,
            @RequestParam(required=false) String partyIdFrom, @RequestParam(required=false) String partyId) {
        var page = invoices.search(new Search(Math.min(Math.max(limit,1),100),0,invoiceId,invoiceTypeId,statusId,partyIdFrom,partyId));
        String rows = page.invoices().stream().map(i -> "<tr><td><a href=\"/api/accounting/invoices/"+e(i.invoiceId())+"\">"+e(i.invoiceId())+"</a></td><td>"+e(i.invoiceTypeDescription())+"</td><td>"+e(i.statusDescription())+"</td><td>"+e(i.partyIdFrom())+" → "+e(i.partyId())+"</td><td>"+e(String.valueOf(i.invoiceTotal()))+" "+e(i.currencyUomId())+"</td><td>"+e(String.valueOf(i.outstandingTotal()))+"</td><td><a href=\"/accounting/control/viewInvoice?invoiceId="+e(i.invoiceId())+"\">Legacy detail</a></td></tr>").collect(Collectors.joining());
        return """
          <!doctype html><html><head><meta charset="utf-8"><title>OFBiz Accounting - Invoices</title>
          <style>:root{--emerald:#1BC5BD;--light:#dcfffd;--dark:#133d3b}*{box-sizing:border-box}body{margin:0;font:14px Arial;color:#233}header{height:72px;background:var(--emerald);display:flex;align-items:center;padding:10px 24px}header img{width:126px}nav{background:var(--dark);padding:11px 20px}nav a{color:white;margin-right:18px;text-decoration:none}.subnav{background:var(--light);padding:12px 20px}.subnav a{color:var(--dark);margin-right:14px}main{padding:24px}form{padding:16px;background:#f5f7f7;display:flex;gap:10px;flex-wrap:wrap}input{padding:8px;border:1px solid #aaa}button{background:var(--emerald);border:0;padding:9px 18px}table{border-collapse:collapse;width:100%%;margin-top:20px}th,td{border-bottom:1px solid #ddd;text-align:left;padding:10px}th{background:var(--light)}.note{color:#555}</style></head>
          <body><header><img src="/images/ofbiz_logo.png" alt="OFBiz logo"><h1>Accounting</h1></header>
          <nav><a href="/webtools">Webtools</a><a href="/ordermgr">Order Manager</a><a href="/partymgr">Party Manager</a><a href="/catalog">Product Catalog</a><a href="/accounting">Accounting</a></nav>
          <div class="subnav"><strong>Invoices</strong> · <a href="/accounting/control/findPayments">Payments</a><a href="/accounting/control/findPaymentGroup">Payment Groups</a><a href="/accounting/control/FindAcctgTrans">Transactions</a><a href="/accounting/control/findBillingAccount">Billing Accounts</a><a href="/accounting/control/FindFinAccount">Financial Accounts</a><a href="/accounting/control/FindTaxAuthority">Tax Authorities</a><a href="/accounting/control/FindAgreement">Agreements</a><a href="/accounting/control/FindFixedAsset">Fixed Assets</a><a href="/accounting/control/FindBudget">Budgets</a><a href="/accounting/control/EditGlobalGlAccount">GL Settings</a><a href="/accounting/control/FindParty">Companies</a></div>
          <main><h2>Invoices — modern read projection</h2><form method="get"><input name="invoiceId" placeholder="Invoice ID" value="%s"><input name="invoiceTypeId" placeholder="Type" value="%s"><input name="statusId" placeholder="Status" value="%s"><input name="partyIdFrom" placeholder="From party" value="%s"><input name="partyId" placeholder="To party" value="%s"><button>Search</button></form>
          <p class="note">Source: legacy-ofbiz-postgres. Totals are SQL-derived and must be reconciled with OFBiz InvoiceWorker before write cutover.</p>
          <table><thead><tr><th>ID</th><th>Type</th><th>Status</th><th>Parties</th><th>Total</th><th>Outstanding</th><th>Legacy</th></tr></thead><tbody>%s</tbody></table></main></body></html>
          """.formatted(e(invoiceId),e(invoiceTypeId),e(statusId),e(partyIdFrom),e(partyId),rows);
    }
    private static String e(Object value) { return value == null ? "" : String.valueOf(value).replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;"); }
}
