# Local Hybrid Development

This starts a minimal hybrid stack: legacy OFBiz plus a modern gateway and the first modern service.

## Start

Install legacy UI vendor assets once before starting if the OFBiz image was built with `-PskipNpmInstall`:

```shell
npm ci --prefix themes/common-theme/webapp/common-theme/js
```

```shell
docker compose -p erp-local -f local-dev/docker-compose.yml up -d
```

## URLs

- Hybrid UI entry: `https://localhost:18080/webtools`
- Modern service health: `https://localhost:18080/api/notifications/health`
- Direct legacy fallback: `https://localhost:18443/webtools`

The direct legacy fallback must render same-origin asset and form URLs on `https://localhost:18443`. Gateway-served legacy pages must render those URLs on `https://localhost:18080`. This avoids cross-origin `less.js`/XHR failures and keeps CSS/JS loading stable.

## Login

- User: `admin`
- Password: `ofbiz`

## Stop

```shell
docker compose -p erp-local -f local-dev/docker-compose.yml down
```

Use `down -v` if you need a full data reload/reset.
