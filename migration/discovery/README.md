# Migration Discovery

Generated discovery reports for the OFBiz-to-microservices migration.

Phase 1 extraction target: Accounting > Invoices. The first modern slice is a read-only invoice projection over legacy OFBiz Postgres, exposed through `modern-accounting-invoice-service` at `/api/accounting/invoices` and `/modern/accounting/invoices`.

Run:

```shell
python3 migration/discovery/generate_discovery.py
```

Reports:

- `components.md`: active OFBiz components and contributed resources.
- `routes.md`: webapps, mount points, controller request maps.
- `services.md`: service definitions and engines.
- `entities.md`: entity and view-entity definitions.
- `integrations.md`: keyword-based external integration inventory.
