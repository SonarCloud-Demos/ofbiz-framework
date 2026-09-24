# Contract registry

Store reviewed OpenAPI documents in `http/` and AsyncAPI or JSON Schema event
contracts in `events/`. Contracts are versioned with their producers and must
be backwards-compatible while a deployed consumer still depends on them.

Every contract records its owner, data classification, compatibility policy,
and lifecycle state. Generated code is build output and is not committed here.
