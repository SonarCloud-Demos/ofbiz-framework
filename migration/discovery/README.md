# Migration Discovery

Generated discovery reports for the OFBiz-to-microservices migration.

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
