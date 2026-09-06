#!/usr/bin/env python3
import json
import os
from decimal import Decimal
from html import escape
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

import psycopg
from psycopg.rows import dict_row


MAX_LIMIT = 100


def db_config():
    return {
        "host": os.getenv("DB_HOST", "legacy-ofbiz-db"),
        "port": int(os.getenv("DB_PORT", "5432")),
        "dbname": os.getenv("DB_NAME", os.getenv("OFBIZ_POSTGRES_OFBIZ_DB", "ofbizmaindb")),
        "user": os.getenv("DB_USER", os.getenv("OFBIZ_POSTGRES_OFBIZ_USER", "ofbiz")),
        "password": os.getenv("DB_PASSWORD", os.getenv("OFBIZ_POSTGRES_OFBIZ_PASSWORD", "")),
    }


def to_json_value(value):
    if isinstance(value, Decimal):
        return str(value)
    if hasattr(value, "isoformat"):
        return value.isoformat()
    return value


def clean_row(row):
    return {key: to_json_value(value) for key, value in row.items()}


def parse_limit(query):
    raw = query.get("limit", ["25"])[0]
    try:
        return min(max(int(raw), 1), MAX_LIMIT)
    except ValueError:
        return 25


def parse_offset(query):
    raw = query.get("offset", ["0"])[0]
    try:
        return max(int(raw), 0)
    except ValueError:
        return 0


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        query = parse_qs(parsed.query)

        try:
            if path in ("/health", "/api/accounting/invoices/health"):
                self.respond(200, {
                    "status": "UP",
                    "service": os.getenv("SERVICE_NAME", "modern-accounting-invoice-service"),
                    "stack": "modern",
                    "slice": "accounting-invoices",
                })
                return

            if path == "/api/accounting/invoices":
                self.respond(200, self.list_invoices(query))
                return

            if path in ("/modern/accounting/invoices", "/accounting/control/findInvoices"):
                self.respond_html(200, self.render_invoice_list(query))
                return

            prefix = "/api/accounting/invoices/"
            if path.startswith(prefix):
                invoice_id = path[len(prefix):]
                if not invoice_id or "/" in invoice_id:
                    self.respond(404, {"error": "not_found", "path": parsed.path})
                    return
                invoice = self.get_invoice(invoice_id)
                if invoice is None:
                    self.respond(404, {"error": "invoice_not_found", "invoiceId": invoice_id})
                    return
                self.respond(200, invoice)
                return

            self.respond(404, {"error": "not_found", "path": parsed.path})
        except psycopg.Error as exc:
            self.respond(503, {"error": "legacy_db_unavailable", "detail": exc.__class__.__name__})

    def list_invoices(self, query):
        limit = parse_limit(query)
        offset = parse_offset(query)
        filters = []
        params = {"limit": limit, "offset": offset}

        for field, column in (
            ("invoiceId", "i.invoice_id"),
            ("invoiceTypeId", "i.invoice_type_id"),
            ("statusId", "i.status_id"),
            ("partyId", "i.party_id"),
            ("partyIdFrom", "i.party_id_from"),
        ):
            value = query.get(field, [None])[0]
            if value:
                filters.append(f"{column} = %({field})s")
                params[field] = value

        where_clause = ""
        if filters:
            where_clause = "where " + " and ".join(filters)

        sql = f"""
            select
                i.invoice_id as "invoiceId",
                i.invoice_type_id as "invoiceTypeId",
                it.description as "invoiceTypeDescription",
                i.party_id_from as "partyIdFrom",
                i.party_id as "partyId",
                i.status_id as "statusId",
                si.description as "statusDescription",
                i.invoice_date as "invoiceDate",
                i.due_date as "dueDate",
                i.paid_date as "paidDate",
                i.currency_uom_id as "currencyUomId",
                i.description,
                coalesce(items.item_count, 0) as "itemCount",
                coalesce(items.item_total, 0) as "invoiceTotal",
                coalesce(payments.applied_total, 0) as "appliedTotal",
                coalesce(items.item_total, 0) - coalesce(payments.applied_total, 0) as "outstandingTotal"
            from invoice i
            left join invoice_type it on it.invoice_type_id = i.invoice_type_id
            left join status_item si on si.status_id = i.status_id
            left join (
                select
                    invoice_id,
                    count(*) as item_count,
                    sum(coalesce(quantity, 1) * coalesce(amount, 0)) as item_total
                from invoice_item
                group by invoice_id
            ) items on items.invoice_id = i.invoice_id
            left join (
                select invoice_id, sum(amount_applied) as applied_total
                from payment_application
                where invoice_id is not null
                group by invoice_id
            ) payments on payments.invoice_id = i.invoice_id
            {where_clause}
            order by i.invoice_date desc nulls last, i.invoice_id desc
            limit %(limit)s offset %(offset)s
        """

        with psycopg.connect(**db_config(), row_factory=dict_row) as conn:
            with conn.cursor() as cur:
                cur.execute(sql, params)
                invoices = [clean_row(row) for row in cur.fetchall()]

                cur.execute(f"select count(*) as total from invoice i {where_clause}", params)
                total = cur.fetchone()["total"]

        return {
            "data": invoices,
            "pagination": {"limit": limit, "offset": offset, "total": total},
            "source": "legacy-ofbiz-postgres",
            "note": "Read-only Phase 1 invoice projection. Totals are SQL-derived and must be reconciled with OFBiz InvoiceWorker before write cutover.",
        }

    def render_invoice_list(self, query):
        result = self.list_invoices(query)
        rows = []
        for invoice in result["data"]:
            invoice_id = escape(invoice["invoiceId"])
            status = escape(invoice.get("statusDescription") or invoice.get("statusId") or "")
            invoice_type = escape(invoice.get("invoiceTypeDescription") or invoice.get("invoiceTypeId") or "")
            party_from = escape(invoice.get("partyIdFrom") or "")
            party_to = escape(invoice.get("partyId") or "")
            total = escape(invoice.get("invoiceTotal") or "0")
            applied = escape(invoice.get("appliedTotal") or "0")
            outstanding = escape(invoice.get("outstandingTotal") or "0")
            currency = escape(invoice.get("currencyUomId") or "")
            rows.append(f"""
                <tr>
                    <td>
                        <a href="/api/accounting/invoices/{invoice_id}">{invoice_id}</a>
                        <div><a class="legacy-link" href="/accounting/control/viewInvoice?invoiceId={invoice_id}">Legacy detail</a></div>
                    </td>
                    <td>{invoice_type}</td>
                    <td>{status}</td>
                    <td>{party_from}</td>
                    <td>{party_to}</td>
                    <td class="number">{total} {currency}</td>
                    <td class="number">{applied} {currency}</td>
                    <td class="number">{outstanding} {currency}</td>
                </tr>
            """)

        body = "\n".join(rows)
        total_count = result["pagination"]["total"]
        action = "/accounting/control/findInvoices"
        invoice_id_value = escape(query.get("invoiceId", [""])[0])
        invoice_type_value = escape(query.get("invoiceTypeId", [""])[0])
        status_value = escape(query.get("statusId", [""])[0])
        party_from_value = escape(query.get("partyIdFrom", [""])[0])
        party_value = escape(query.get("partyId", [""])[0])
        return f"""<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title>Modern Accounting Invoices</title>
    <style>
        body {{ font-family: Arial, sans-serif; margin: 2rem; color: #172033; }}
        .eyebrow {{ color: #586a84; font-size: 0.8rem; letter-spacing: 0.08em; text-transform: uppercase; }}
        h1 {{ margin-bottom: 0.25rem; }}
        .note {{ background: #fff7df; border: 1px solid #f2d388; padding: 1rem; margin: 1.5rem 0; }}
        table {{ border-collapse: collapse; width: 100%; }}
        th, td {{ border-bottom: 1px solid #d8dee8; padding: 0.7rem; text-align: left; }}
        th {{ background: #edf2f7; }}
        .number {{ text-align: right; font-variant-numeric: tabular-nums; }}
        form {{ display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 0.75rem; margin: 1.5rem 0; }}
        label {{ color: #374963; font-size: 0.85rem; }}
        input {{ box-sizing: border-box; margin-top: 0.25rem; padding: 0.45rem; width: 100%; }}
        button {{ align-self: end; background: #005eb8; border: 0; color: white; cursor: pointer; padding: 0.55rem 0.8rem; }}
        a {{ color: #005eb8; }}
        .legacy-link {{ color: #586a84; font-size: 0.8rem; }}
        @media (max-width: 900px) {{ form {{ grid-template-columns: 1fr 1fr; }} }}
    </style>
</head>
<body>
    <div class="eyebrow">Modern microservice slice</div>
    <h1>Accounting Invoices</h1>
    <p>Read-only invoice projection served by <code>modern-accounting-invoice-service</code>.</p>
    <p>This page replaces legacy <code>/accounting/control/findInvoices</code> when accessed through the hybrid gateway. Other Accounting routes still fall through to OFBiz.</p>
    <div class="note">
        Phase 1 intentionally exposes migration issues: legacy DB read dependency, invoice/item/payment joins,
        SQL-derived totals, and rounding deltas such as paid invoice <code>8009</code>.
        OFBiz remains source of truth until totals are reconciled with <code>InvoiceWorker</code>.
    </div>
    <form method="get" action="{action}">
        <label>Invoice ID<input name="invoiceId" value="{invoice_id_value}"></label>
        <label>Type<input name="invoiceTypeId" value="{invoice_type_value}" placeholder="SALES_INVOICE"></label>
        <label>Status<input name="statusId" value="{status_value}" placeholder="INVOICE_PAID"></label>
        <label>From party<input name="partyIdFrom" value="{party_from_value}"></label>
        <label>To party<input name="partyId" value="{party_value}"></label>
        <button type="submit">Search</button>
    </form>
    <p>Total invoices: {total_count}. API: <a href="/api/accounting/invoices?limit=10">/api/accounting/invoices</a>.</p>
    <table>
        <thead>
            <tr>
                <th>Invoice</th>
                <th>Type</th>
                <th>Status</th>
                <th>From</th>
                <th>To</th>
                <th class="number">Total</th>
                <th class="number">Applied</th>
                <th class="number">Outstanding</th>
            </tr>
        </thead>
        <tbody>{body}</tbody>
    </table>
</body>
</html>"""

    def get_invoice(self, invoice_id):
        header_sql = """
            select
                i.invoice_id as "invoiceId",
                i.invoice_type_id as "invoiceTypeId",
                it.description as "invoiceTypeDescription",
                i.party_id_from as "partyIdFrom",
                i.party_id as "partyId",
                i.role_type_id as "roleTypeId",
                i.status_id as "statusId",
                si.description as "statusDescription",
                i.billing_account_id as "billingAccountId",
                i.contact_mech_id as "contactMechId",
                i.invoice_date as "invoiceDate",
                i.due_date as "dueDate",
                i.paid_date as "paidDate",
                i.invoice_message as "invoiceMessage",
                i.reference_number as "referenceNumber",
                i.description,
                i.currency_uom_id as "currencyUomId",
                coalesce(items.item_count, 0) as "itemCount",
                coalesce(items.item_total, 0) as "invoiceTotal",
                coalesce(payments.applied_total, 0) as "appliedTotal",
                coalesce(items.item_total, 0) - coalesce(payments.applied_total, 0) as "outstandingTotal"
            from invoice i
            left join invoice_type it on it.invoice_type_id = i.invoice_type_id
            left join status_item si on si.status_id = i.status_id
            left join (
                select
                    invoice_id,
                    count(*) as item_count,
                    sum(coalesce(quantity, 1) * coalesce(amount, 0)) as item_total
                from invoice_item
                group by invoice_id
            ) items on items.invoice_id = i.invoice_id
            left join (
                select invoice_id, sum(amount_applied) as applied_total
                from payment_application
                where invoice_id is not null
                group by invoice_id
            ) payments on payments.invoice_id = i.invoice_id
            where i.invoice_id = %(invoice_id)s
        """
        items_sql = """
            select
                invoice_item_seq_id as "invoiceItemSeqId",
                invoice_item_type_id as "invoiceItemTypeId",
                product_id as "productId",
                taxable_flag as "taxableFlag",
                quantity,
                amount,
                coalesce(quantity, 1) * coalesce(amount, 0) as "lineTotal",
                description
            from invoice_item
            where invoice_id = %(invoice_id)s
            order by invoice_item_seq_id
        """
        payments_sql = """
            select
                payment_application_id as "paymentApplicationId",
                payment_id as "paymentId",
                invoice_item_seq_id as "invoiceItemSeqId",
                billing_account_id as "billingAccountId",
                amount_applied as "amountApplied"
            from payment_application
            where invoice_id = %(invoice_id)s
            order by payment_application_id
        """
        status_sql = """
            select
                status_id as "statusId",
                status_date as "statusDate",
                change_by_user_login_id as "changeByUserLoginId"
            from invoice_status
            where invoice_id = %(invoice_id)s
            order by status_date
        """

        with psycopg.connect(**db_config(), row_factory=dict_row) as conn:
            with conn.cursor() as cur:
                cur.execute(header_sql, {"invoice_id": invoice_id})
                header = cur.fetchone()
                if header is None:
                    return None

                cur.execute(items_sql, {"invoice_id": invoice_id})
                items = [clean_row(row) for row in cur.fetchall()]

                cur.execute(payments_sql, {"invoice_id": invoice_id})
                payments = [clean_row(row) for row in cur.fetchall()]

                cur.execute(status_sql, {"invoice_id": invoice_id})
                statuses = [clean_row(row) for row in cur.fetchall()]

        return {
            "data": {
                **clean_row(header),
                "items": items,
                "paymentApplications": payments,
                "statusHistory": statuses,
            },
            "source": "legacy-ofbiz-postgres",
            "note": "Read-only Phase 1 invoice projection. Totals are SQL-derived and must be reconciled with OFBiz InvoiceWorker before write cutover.",
        }

    def log_message(self, fmt, *args):
        print("%s - %s" % (self.address_string(), fmt % args), flush=True)

    def respond(self, status, body):
        data = json.dumps(body, default=to_json_value).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def respond_html(self, status, body):
        data = body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def main():
    port = int(os.getenv("PORT", "8080"))
    server = HTTPServer(("0.0.0.0", port), Handler)
    print(f"modern-accounting-invoice-service listening on {port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
