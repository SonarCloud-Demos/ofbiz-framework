# Waived Phase 3 Azure acceptance

Status: **WAIVED by project decision on 2026-09-24.** No Azure infrastructure was available, so the checks below were not executed and must not be described as passed.

The waived acceptance procedure would otherwise require dated evidence that:

- Entra authorization code/PKCE validates the configured tenant, issuer, audience, signature, nonce/state, roles, login, and logout;
- browser tokens are absent and encrypted shared session state survives BFF revision changes;
- Front Door is the only public entry point, direct shell/OFBiz origins reject access, and forwarded-host rules reject spoofing;
- an approved identity mapping signs one user into the modern test and proxied legacy route without a password exchange;
- representative allowed and denied roles match OFBiz authorization;
- W3C trace context spans edge, BFF, identity access, adapter, and OFBiz, with exercised alerts and sanitized errors;
- forced modern failure and the route kill switch restore the legacy route without impairing unrelated legacy paths;
- workload secrets used by repository/local tests are replaced by managed identity and Key Vault-backed rotation.

If the waiver is revisited, store evidence under `docs/migration/phase3/evidence/<date>/`. Repository checks do not substitute for these deployment tests.
