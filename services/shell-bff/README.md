# Shell BFF

The Phase 2 same-origin security boundary owns `/modern/**` and `/bff/**`.
It uses Entra authorization-code flow with PKCE and keeps tokens in the
server-side session. Browser-visible identity responses contain only display,
tenant and authority data.

Required configuration is injected at runtime: `ENTRA_CLIENT_ID`,
`ENTRA_CLIENT_SECRET`, `ENTRA_ISSUER_URI`, `ENTRA_AUTHORIZATION_URI`,
`ENTRA_TOKEN_URI`, `ENTRA_JWK_SET_URI`, `ENTRA_ALLOWED_TENANTS`, and a
minimum 32-byte `LEGACY_BRIDGE_SIGNING_KEY`. Secrets must come from the local
secret mechanism or Key Vault; none have defaults.

The legacy assertion endpoint requires `ROLE_LEGACY_USER` and is reachable only
through the edge's internal authentication subrequest. It returns a signed,
audience-bound assertion valid for 60 seconds in a response header that the edge
forwards to OFBiz; browser code never receives it. OFBiz must
validate signature, issuer, audience, expiry and one-time `jti` use before this
bridge is enabled.
