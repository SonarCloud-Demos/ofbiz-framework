# Azure infrastructure

Terraform state is bootstrapped separately from environment state. Development
and staging consume the reusable `modules/platform` composition and require
Azure AD authentication; secrets are supplied as sensitive variables and are
never committed.

Run `terraform fmt -check -recursive`, initialize with the approved provider
mirror, then use environment-specific backend configuration. Apply only from
the federated deployment workflow after reviewing the saved plan. PostgreSQL
and state storage carry `prevent_destroy`; removing them requires a separately
reviewed recovery and retention decision.

Production is intentionally absent until Phase 0 evidence and the separate
production authorization are complete.
