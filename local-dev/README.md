# Minimal Viable Modern Phase 1

This stack keeps OFBiz authoritative and strangles only Accounting invoice search/list/detail and the deliberately limited invoice-header CRUD demo.

## Start

The build reads npm packages from the registry configured in the host `.npmrc`. The file and corporate CA are mounted as BuildKit secrets and are never copied to an image. Theme installation uses `npm ci --ignore-scripts --legacy-peer-deps`: the lockfile is authoritative, while legacy theme plugins that have not widened their jQuery peer ranges do not block the reproducible install. If Compose does not expand nested defaults on your version, set both paths explicitly:

```sh
export NPM_CONFIG_USERCONFIG="$HOME/.npmrc"
export CORPORATE_CA_FILE="$HOME/.certs/Sonar-CloudFlare-Inspection-Cert.pem"
local-dev/generate-local-tls.sh
docker compose -p erp-local -f local-dev/docker-compose.yml up -d --build
local-dev/smoke-test.sh
```

Never copy `.npmrc` into this repository or pass its token through a build argument/environment variable.

## Routes and rollback

| URL | Owner |
| --- | --- |
| `https://localhost:18080/webtools` | OFBiz through gateway |
| `https://localhost:18080/accounting/control/findInvoices` | modern invoice service |
| `https://localhost:18080/modern/accounting/invoices` | modern invoice service |
| `https://localhost:18080/api/accounting/invoices` | modern invoice service |
| `https://localhost:18080/api/notifications/health` | notification service |
| `https://localhost:18443/...` | direct OFBiz fallback |

Direct debug ports are `18106` (invoice), `18101` (notification), `18443` (OFBiz), and `15432` (legacy PostgreSQL). To roll back invoice search, remove its exact-match gateway location; the generic fallback then routes it to OFBiz. The modern service temporarily reads the legacy DB and limits writes to invoice headers. Do not treat this exception as the target service data-ownership model.
