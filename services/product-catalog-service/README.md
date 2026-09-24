# Product catalog projection service

Phase 3 read-only pilot for product search and basic detail. OFBiz remains the
authoritative writer. This service owns an isolated PostgreSQL projection and
must receive data through the internal ingestion contract; it must never read
the OFBiz database directly.

Build and test with `./gradlew test`. Browser requests go through the shell BFF;
the internal service port is not a public API. See
`docs/migration/phase-3/README.md` for ownership, rollout and rollback status.
