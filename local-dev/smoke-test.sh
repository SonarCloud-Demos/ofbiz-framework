#!/bin/sh
set -eu

gateway=https://localhost:18080
legacy=https://localhost:18443

wait_for() {
  url=$1
  attempts=0
  until curl -kfsS "$url" >/dev/null; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 60 ]; then
      echo "Timed out waiting for $url" >&2
      return 1
    fi
    sleep 2
  done
}

wait_for "$gateway/api/accounting/invoices/health"
wait_for "$gateway/api/notifications/health"
wait_for "$legacy/webtools"

invoice_html=$(curl -kfsS "$gateway/accounting/control/findInvoices?invoiceId=8009")
printf '%s' "$invoice_html" | grep -q 'OFBiz'
printf '%s' "$invoice_html" | grep -q '#1BC5BD'
printf '%s' "$invoice_html" | grep -q 'Legacy detail'
printf '%s' "$invoice_html" | grep -q '8009'

curl -kfsS "$gateway/api/accounting/invoices?limit=2" | grep -q 'legacy-ofbiz-postgres'
curl -kfsS "$gateway/api/accounting/invoices/8009" | grep -q 'lineItems'
legacy_status=$(curl -ksS -o /dev/null -w '%{http_code}' "$gateway/accounting/control/findPayments")
case "$legacy_status" in
  200|302|401) ;;
  *) echo "Unexpected non-strangled Accounting status: $legacy_status" >&2; exit 1 ;;
esac

for base in "$gateway" "$legacy"; do
  curl -kfsS "$base/helveticus/HELVETICUS_EMERALD.less" >/dev/null
  curl -kfsS "$base/helveticus/style.css" >/dev/null
  curl -kfsS "$base/common/js/node_modules/jquery-ui-dist/jquery-ui.min.js" >/dev/null
done

test_id="MSVCTEST$(date +%s)"
payload=$(printf '{"invoiceId":"%s","invoiceTypeId":"SALES_INVOICE","partyIdFrom":"Company","partyId":"DemoCustomer","statusId":"INVOICE_IN_PROCESS","currencyUomId":"USD"}' "$test_id")
curl -kfsS -H 'Content-Type: application/json' -d "$payload" "$gateway/api/accounting/invoices" | grep -q "$test_id"
curl -kfsS -X PUT -H 'Content-Type: application/json' -d "$payload" "$gateway/api/accounting/invoices/$test_id" | grep -q "$test_id"
curl -kfsS -X DELETE "$gateway/api/accounting/invoices/$test_id" >/dev/null

echo "Minimal Viable Modern Phase 1 smoke test passed"
