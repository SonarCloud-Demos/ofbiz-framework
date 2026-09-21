#!/bin/sh
set -eu
gateway=https://localhost:18080
legacy=https://localhost:18443
tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT

curl -kfsS "$gateway/api/notifications/health" | grep 'modern-notification-service'
curl -kfsS "$gateway/api/accounting/invoices/health" | grep 'modern-accounting-invoice-service'
curl -kfsS "$gateway/accounting/control/findInvoices?invoiceId=8009" -o "$tmp_dir/modern.html"
grep 'OFBiz logo' "$tmp_dir/modern.html"
grep '#1BC5BD' "$tmp_dir/modern.html"
grep 'Legacy detail' "$tmp_dir/modern.html"
grep '8009' "$tmp_dir/modern.html"
curl -kfsS "$gateway/api/accounting/invoices?limit=2" | grep 'legacy-ofbiz-postgres'
curl -kfsS "$gateway/api/accounting/invoices/8009" | grep 'lineItems'
legacy_status=$(curl -ksS -o "$tmp_dir/legacy.html" -w '%{http_code}' "$legacy/accounting/control/findInvoices")
payments_status=$(curl -ksS -o "$tmp_dir/payments.html" -w '%{http_code}' "$gateway/accounting/control/findPayments")
case "$legacy_status" in 200|401) ;; *) echo "Unexpected legacy invoice status: $legacy_status" >&2; exit 1;; esac
case "$payments_status" in 200|401) ;; *) echo "Unexpected legacy payments status: $payments_status" >&2; exit 1;; esac

for origin in "$gateway" "$legacy"; do
  curl -kfsS "$origin/helveticus/HELVETICUS_EMERALD.less" >/dev/null
  curl -kfsS "$origin/helveticus/style.css" >/dev/null
  curl -kfsS "$origin/common/js/node_modules/jquery-ui-dist/jquery-ui.min.js" >/dev/null
done

test_id="MSVCTEST$(date +%s)"
payload="{\"invoiceId\":\"$test_id\",\"invoiceTypeId\":\"SALES_INVOICE\",\"partyIdFrom\":\"Company\",\"partyId\":\"DemoCustomer\",\"roleTypeId\":\"BILL_TO_CUSTOMER\",\"statusId\":\"INVOICE_IN_PROCESS\",\"invoiceDate\":\"2026-09-21T00:00:00Z\",\"description\":\"Phase 1 smoke test\",\"currencyUomId\":\"USD\"}"
curl -kfsS -H 'Content-Type: application/json' -d "$payload" "$gateway/api/accounting/invoices" | grep "$test_id"
payload="{\"invoiceTypeId\":\"SALES_INVOICE\",\"partyIdFrom\":\"Company\",\"partyId\":\"DemoCustomer\",\"roleTypeId\":\"BILL_TO_CUSTOMER\",\"statusId\":\"INVOICE_IN_PROCESS\",\"invoiceDate\":\"2026-09-21T00:00:00Z\",\"description\":\"Phase 1 smoke test updated\",\"currencyUomId\":\"USD\"}"
curl -kfsS -X PUT -H 'Content-Type: application/json' -d "$payload" "$gateway/api/accounting/invoices/$test_id" | grep 'smoke test updated'
curl -kfsS -X DELETE "$gateway/api/accounting/invoices/$test_id"
guarded_status=$(curl -ksS -o "$tmp_dir/guarded-delete.json" -w '%{http_code}' -X DELETE "$gateway/api/accounting/invoices/8009")
[ "$guarded_status" = 409 ] || { echo "Expected guarded delete to return 409, got $guarded_status" >&2; exit 1; }

echo 'Phase 1 read/write and legacy fallback smoke tests passed'
