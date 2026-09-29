# Phase 1 — Monorepo and local-development foundation

## Delivered baseline

- The root Gradle graph includes the sample service, web shell, route registry, contracts, infrastructure layout, local configuration, and a registration guard.
- `./modern` provides `bootstrap`, `build`, `test`, `up`, `down`, `logs`, and `smoke`; its build command delegates directly to the root Gradle build.
- The hybrid Compose stack exposes one URL, a modern platform test route, a proxied legacy route, isolated PostgreSQL databases, RabbitMQ behind the messaging boundary, a local storage-port probe, development-only Keycloak, and an OpenTelemetry collector. Full Blob semantics require Azurite or ephemeral Azure because some development environments cannot trust Microsoft Container Registry.
- The modern route has reviewed metadata and an accessible marker. It calls its sample service through the same-origin shell proxy and has a legacy fallback.
- The platform service has liveness/readiness endpoints, structured startup output, correlation propagation, tests, coverage XML, and a non-root container.
- Node/npm and Terraform versions are pinned. Phase 1 infrastructure checks are static and never apply Azure resources.

## Run locally

```text
./modern bootstrap
./modern build
./modern up
./modern smoke
./modern down
```

`up` builds the legacy demo image and may take several minutes on first use. It is idempotent and does not destroy volumes. Any future state-reset command must be explicit and destructive by name.

## Gate status

Repository-controlled Phase 1 implementation is present. Production traffic remains unchanged. CI runs the root build, and Sonar runs the root `build` plus analysis. Before declaring the organizational phase gate complete, configure the Sonar project quality gate for at least 80% coverage on new code and demonstrate the commands above on a documented clean development machine and in CI.
