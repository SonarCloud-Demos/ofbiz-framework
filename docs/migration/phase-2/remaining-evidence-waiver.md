# Phase 2 Azure, Entra, security and evidence waiver

## Decision

On 2026-09-24, the migration sponsor waived every remaining Phase 2 exit-gate
requirement involving Azure, Microsoft Entra ID, the OFBiz assertion consumer,
penetration/security testing, environment validation, rollout exercises, or
retained external evidence.

This decision closes the Phase 2 exit gate by sponsor waiver. It is an
administrative progression decision, not evidence that the waived controls
exist or work.

## Waived implementation and evidence

- deployment of Front Door, WAF, APIM, the BFF, or supporting resources to
  Azure;
- real Entra registration and sign-in, sign-out, renewal, expiry, revocation,
  role-change, Conditional Access, and tenant-isolation exercises;
- implementation and validation of the OFBiz assertion consumer, identity
  mapping, signature/audience/expiry checks, and one-time `jti` replay defense;
- external validation of TLS, origin restriction, trusted proxy/client-IP
  handling, route ownership, headers, cookies, redirects, uploads, and
  timeouts;
- penetration testing, security review, and accessibility testing;
- route parity, representative performance, internal-user and all-user rollout,
  soak, monitoring, and direct-legacy rollback exercises;
- any other external or retained evidence otherwise required by the Phase 2
  exit gate.

## Evidence retained

- repository route catalog, local edge reference configuration and modern
  shell;
- BFF implementation and automated PKCE, session, CSRF, role, tenant/issuer,
  and signed-assertion tests;
- trusted generation/identity-header handling and local synthetic checks;
- statically validated Front Door/WAF Terraform configuration;
- threat model, route-validation procedure and rollback guidance.

## Consequences

Phase 2 is complete by sponsor waiver, but Azure and Entra remain undeployed
and unvalidated. OFBiz cannot safely consume the bridge assertion because its
consumer has not been implemented. The solution has not passed penetration,
environment, rollout, or production-readiness testing.

The waived controls become mandatory release gates before any Azure/Entra
deployment or assertion bridge handles real users, credentials, sessions, or
business data. Later phases may proceed only without representing these Phase 2
capabilities as operational or production-ready.

| Field | Value |
| --- | --- |
| Decision | Waive all remaining Phase 2 Azure, Entra, security and evidence requirements |
| Date | 2026-09-24 |
| Approver | Migration sponsor; identity not recorded |
| Phase 2 status | Complete by sponsor waiver |
| Azure/Entra status | Not deployed or validated |
| OFBiz assertion consumer | Not implemented; waived |
| Production readiness | Not established |
