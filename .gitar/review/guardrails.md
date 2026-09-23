# Phase 0 engineering and governance guardrails

## Architecture

- Use business bounded contexts, not current package names, as candidate service boundaries.
- A modern service owns its writes and database objects. Other components use published APIs or events.
- Modern services may not import OFBiz code. Only the temporary anti-corruption adapter may do so.
- Cross-service source imports are limited to generated clients and published schemas; never share domain or persistence models.
- Local ACID transactions remain inside one service. Cross-service flows use explicit sagas and compensation.
- Every architecture exception has an owner, rationale, control, and expiry date.

Sonar model `4987d1a6-81fe-4f94-86a2-75d56491b7a2` encodes initial Java service and TypeScript UI boundaries. Confirm enforcement in the next SonarQube analysis.

## Security and privacy

- Microsoft Entra identities and managed identities are the default trust anchors.
- Workloads and CI use least privilege and short-lived/federated credentials.
- Public ingress terminates at approved edge services; origins and data services are private by default.
- Store secrets and certificates in Key Vault; do not place them in Terraform values/state outputs, source, images, logs, or events.
- Authenticate and authorize at every service boundary. Network location does not confer trust.
- Threat-model each materially different vertical slice and the coexistence identity bridge.
- Classify data before defining event payloads, logging, retention, backups, or cross-region replication.
- Use allowlist structured logging and test redaction of tokens, credentials, financial data, and PII.

Relevant project Sonar rules explicitly require protection from command, LDAP, XPath, database-query, XML external-entity, archive-extraction, and equivalent injection/resource attacks in Java and JavaScript.

## Delivery and quality

- Build an immutable artifact once and promote the same digest through environments.
- Pull requests run affected builds plus contract, architecture, dependency, secret, IaC and security checks.
- Database changes use expand/contract and remain compatible through the rollback window.
- APIs and events pass backward-compatibility checks before release.
- Every service provides health/readiness, traces, metrics, structured logs, alerts, runbooks, ownership and SLO metadata.
- Every modern UI route passes authorization, accessibility, marker, CSP, browser and API-allowlist tests.
- Feature flags and strangler routes require an owner and expiry date.

## Cost and resource governance

Required Azure resource tags:

| Tag | Example / rule |
| --- | --- |
| `application` | `ofbiz-modernization` |
| `environment` | `dev`, `test`, `staging`, or `prod` |
| `service` | Bounded-context or platform capability name |
| `owner` | Approved team identifier, not an individual's email |
| `cost-center` | Supplied by finance |
| `data-classification` | Approved classification vocabulary |
| `managed-by` | `terraform` |
| `criticality` | Approved tier/SLO class |
| `expiry` | Required for ephemeral resources; ISO date |

Budgets and alerts exist per environment and service allocation. Non-production services scale down or to zero where the workflow permits. Exceptions to approved regions or SKUs require a recorded decision.

## Terraform governance

- Pin Terraform and provider versions and review lockfile changes.
- Use encrypted, private, versioned remote state with separate environment identities and recovery tests.
- CI authenticates through OIDC; protected applies require reviewed plans.
- Run formatting, validation, lint, policy, security and module tests before apply.
- Detect drift on a schedule. Reconcile authorized break-glass changes in code immediately.
- Never expose sensitive values as outputs or put secrets into state when a Key Vault reference is possible.
