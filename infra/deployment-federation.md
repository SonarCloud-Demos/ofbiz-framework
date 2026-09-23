# Federated deployment contract

Development and staging deployments run in protected GitHub environments on an
organization-managed runner with Terraform, Azure CLI, policy scanners, image
scanner, signer, and approved registry configuration. The workflow receives an
Azure OIDC token (`id-token: write`) and exchanges it for a narrowly scoped
deployment identity; no client secret is stored.

The identity can read Terraform state and plan the target environment. Apply
requires environment approval and a separate role scoped to that environment.
Plans are saved and reviewed; applies consume the exact saved plan. Artifacts
are promoted by digest, accompanied by SBOM and provenance, signature-verified
before deployment, canaried on a new Container Apps revision, and rolled back
by traffic weight. A scheduled job runs `terraform plan -detailed-exitcode` and
alerts on exit code 2.

Repository/environment administrators must configure the tenant, subscription,
client IDs, backend coordinates, approvers, alert destination, and retention.
Those organization-specific values are deliberately not committed here.
