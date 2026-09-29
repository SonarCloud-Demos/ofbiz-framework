# OFBiz current architecture

## Purpose and scope

This document describes the architecture present in this repository as inspected on 2026-09-24 at commit `47ec364a5830cb85b80eff93f5d9e778bdda15c3`. It is a description of the checked-in system, not a generic description of Apache OFBiz and not a proposed target architecture.

The current system is a **componentized Java modular monolith**. Framework, domain applications, themes, and any installed plugins are discovered as OFBiz components, compiled into one Gradle application, loaded into one JVM, and normally exposed through one embedded Tomcat server. Components share the entity engine, service engine, security context, transaction manager, classpath, and databases. Component boundaries organize code and configuration, but they are not process or network boundaries.

No independently deployable application is checked in under `services/` or `web/`. Those directories currently contain ignored Gradle caches and build products left in the working tree, but no tracked source files. They are therefore not part of the source-controlled architecture described here.

## Architecture at a glance

```text
Browser / REST client / reverse proxy
                  |
       HTTP 8080 / HTTPS 8443 / optional AJP 8009
                  |
       Embedded Tomcat (CatalinaContainer)
                  |
  +---------------+------------------------------+
  | OFBiz web applications                       |
  | controller.xml -> events/services -> views   |
  | widgets + FreeMarker/Groovy/JavaScript        |
  +---------------+------------------------------+
                  |
        LocalDispatcher / service engine
  Java | Groovy | MiniLang | scripts | entity-auto
       | service groups | ECA/MCA | scheduler
                  |
      Delegator / entity engine / transactions
    model entities + view entities + entity ECA
                  |
       JDBC datasource groups (main/OLAP/tenant)
                  |
     H2 by default; PostgreSQL and others configurable
```

The normal request path is: Tomcat selects a mounted web application; the OFBiz control servlet reads that application's controller mappings; an event or service runs; business services use a `Delegator` for persistence and a `LocalDispatcher` for other services; the controller renders a widget or template view or returns an HTTP/REST response. The service and entity event-condition-action rules can trigger additional work without a direct Java call.

## Repository and build structure

### Root build

The root is a Gradle multi-project build named `ofbiz` (`settings.gradle`). Java 17 is the source and target level. The application entry point is `org.apache.ofbiz.base.start.Start` (`build.gradle`). The build does not create a conventional independent artifact for every business module. Instead, it aggregates the Java, Groovy, resources, configuration, DTDs, and libraries of every active component into root source sets and a common runtime classpath.

Important root files and directories are:

| Path | Architectural role |
| --- | --- |
| `settings.gradle`, `common.gradle` | Discover enabled components and include them as Gradle subprojects. |
| `build.gradle`, `dependencies.gradle` | Assemble the common application, dependencies, tests, distribution, data-loading tasks, and server launch tasks. |
| `framework/` | Runtime platform: bootstrap, component loading, entity and service engines, security, web stack, widgets, embedded Tomcat, REST, and administration tools. |
| `applications/` | ERP domain models, services, workflows, data, and back-office web applications. |
| `themes/` | Shared static assets and selectable UI themes, each mounted as a web application. |
| `plugins/` | Optional extension point. The loader and build support it, but this checkout has no `plugins/` directory. |
| `config/` | Deployment-time configuration override directory. |
| `runtime/` | Mutable runtime state: embedded database files, logs, indexes, generated Catalina state, output, and temporary files. |
| `lib/` | Repository-provided libraries; component `lib/` directories and container `lib-extra/` can add runtime libraries. |
| `docker/`, `Dockerfile` | Container image, initialization logic, configuration templates, hooks, and a PostgreSQL Compose example. |
| `docs/`, `DOCKER.adoc`, `README.md` | Project and operating documentation. |

`buildSrc/` supplies custom Gradle build logic. `framework/resources/templates/` is itself a build/tooling component containing templates used to create components; it is disabled as a runtime component. `framework/start` is added explicitly to the active Gradle projects even though it has no `ofbiz-component.xml`, because it contains the process bootstrap.

### Component discovery and activation

`framework/base/config/component-load.xml` establishes four component roots in order: `framework`, `themes`, `applications`, and `plugins`. The first three have their own `component-load.xml` files that impose an explicit order; a plugin directory is scanned if present. A directory is active only when it contains an `ofbiz-component.xml` whose `enabled` attribute is absent or `true`.

Each `ofbiz-component.xml` is the component's integration contract. It can register:

- classpath directories and resource loaders;
- entity models, view entities, entity-group mappings, entity ECA rules, and seed/demo data;
- service definitions, service ECA/MCA rules, and service groups;
- JVM startup containers;
- embedded web applications and their mount points;
- test suites.

This metadata-driven registration is more important than Java package references for understanding module interaction. A service in one component can be looked up by name from another component; an entity can be used through the shared model registry; and a web controller can include another component's controller via a `component://...` URI.

### Framework modules

The framework components load in this order:

| Component | Responsibility |
| --- | --- |
| `base` | Utilities, configuration, logging, component metadata, startup-container APIs, admin server, and naming support. |
| `entity` | Entity model registry, `Delegator`, JDBC access, caching, transactions, entity queries, and entity ECA. |
| `security` | Authentication and authorization infrastructure, password policy, security entities, and SSO/JWT seed data. |
| `datafile` | Structured flat-file reading and writing. |
| `minilang` | Interpreter/runtime for XML MiniLang business logic. |
| `common` | Shared entities, services, data, web controllers, templates, and broadly reused application utilities. |
| `service` | Service registry and dispatcher, engine adapters, asynchronous jobs, scheduler, service ECA, JMS, RMI, and mail container. |
| `catalina` | Embedded Tomcat configuration and mounting of registered OFBiz web applications. |
| `entityext` | Higher-level entity services, synchronization, tenant services, data loading, and scheduled jobs. |
| `webapp` | Control servlet, request/event/view routing, sessions, and web security plumbing. |
| `widget` | XML screen, form, menu, tree, and portal rendering framework. |
| `testtools` | OFBiz integration-test runner and test services. |
| `webtools` | Administrative UI for entities, services, data import/export, tests, logs, and system operations. |
| `rest-api` | `/rest` API application plus `/docs` OpenAPI/Swagger/ReDoc documentation application. |

The ordering is an initialization order and a broad layering guide, not strict dependency enforcement. All active source is ultimately available in the same runtime.

### ERP application modules

The application loader first registers `datamodel`, then domain components, and finally `commonext`:

| Component | Primary domain and exposed UI |
| --- | --- |
| `datamodel` | Shared canonical entity definitions for accounting, content, human resources, manufacturing, marketing, order, party, product, security, shipment, and work effort. It is primarily a model module, not a UI. |
| `party` | People, organizations, roles, relationships, contact mechanisms, communications, and agreements; Party Manager web app. |
| `securityext` | Application-level security services layered on the framework security component. |
| `content` | Content/data resources, documents, surveys, communication events, websites, and related services; content manager and content-image web apps. |
| `workeffort` | Work efforts, projects, calendars, time sheets, and scheduling; work-effort and iCalendar web apps. |
| `product` | Catalog, products, features, pricing, promotions, inventory, facilities, stores, suppliers, shipments, subscriptions, and rentals; catalog and facility web apps. |
| `manufacturing` | Bills of material, routing, production runs, MRP, and manufacturing UI. |
| `accounting` | Invoices, payments, billing, ledger, budgets, taxes, financial accounts, fixed assets, and costs; accounting, accounts-receivable, and accounts-payable web apps. |
| `humanres` | Employment and human-resources model, services, and UI. |
| `order` | Shopping cart, sales/purchase orders, checkout, reservations, quotes, requirements, returns, and shopping lists; Order Manager UI. |
| `marketing` | Campaigns, contacts, opportunities, and sales-force automation; marketing and SFA UIs. |
| `commonext` | Cross-application extensions and setup, shared controller behavior, and shared JavaScript web apps. |

Most base entities live in `applications/datamodel/entitydef/`. Domain components add view entities, ECA rules, service definitions, implementations, screens, forms, and data. This means the data model is deliberately cross-domain: modules cooperate through shared identifiers and relations rather than owning isolated schemas.

### Themes and presentation

`common-theme` provides shared images/assets and common theme data. `bluelight`, `flatgrey`, `helveticus`, `rainbowstone`, and `tomahawk` provide selectable theme web applications and seed records. Business UIs generally use:

- `WEB-INF/controller.xml` for request maps, events, security, responses, and view maps;
- XML widgets under `widget/` for screens, forms, menus, and trees;
- FreeMarker templates (`.ftl`), Groovy scripts, Java event handlers, and static JavaScript/CSS;
- controller includes and `component://` resource references to reuse other components.

For example, most application controllers include `common`'s common controller; Party Manager additionally includes shared security and content routes; Accounting's AP and AR applications include the main Accounting controller. These are direct in-process composition relationships.

## Runtime architecture and module interaction

### Process bootstrap and containers

`org.apache.ofbiz.base.start.Start` parses commands, initializes configuration, and asks `ContainerLoader` to start the selected loader set. Normal startup uses the `main` loader (`framework/start/.../start.properties`). Alternative loader sets support data loading, tests, and RMI.

The component container first reads component metadata. Registered containers then start in component/load order. The principal normal-runtime containers are:

1. the entity `DelegatorContainer`;
2. the service `ServiceContainer` and mail container;
3. the embedded Catalina container;
4. framework/application-specific containers such as the secret-audit container;
5. the base admin server used for status and controlled shutdown.

The result is one long-running JVM. Scheduled service jobs and asynchronous service work also run inside this deployment unless explicitly routed to JMS or RMI.

### Entity and persistence layer

The entity engine is an active-record-like metadata layer over JDBC, accessed mainly through `Delegator` and `GenericValue`. XML model files define physical entities, relations, keys, field types, and composed view entities. Components contribute to a shared `main` model reader; `entitygroup.xml` resources map entities to datasource groups.

The default delegator maps three logical groups:

- `org.apache.ofbiz` to `localh2` for transactional application data;
- `org.apache.ofbiz.olap` to `localh2olap` for analytical data;
- `org.apache.ofbiz.tenant` to `localh2tenant` for tenant metadata.

Out of the box these are file-backed H2 databases under `runtime/data`. `entityengine.xml` also defines PostgreSQL, MySQL, Oracle, SQL Server, and other datasource templates. Changing databases is configuration, not a different application topology. Entity caching is process-local by default (`distributed-cache-clear-enabled="false"` on the default delegator), which is relevant to any multi-instance deployment.

Entity ECA rules can react to create/update/remove/find operations. The entity engine participates in transactions used by services, so changes made across multiple components can share one transaction.

### Service layer

Business operations are named services registered from component `servicedef/*.xml` files. Callers use a local dispatcher rather than constructing implementation classes directly. A service definition declares inputs, outputs, authentication/export properties, transaction behavior, and an implementation engine.

The configured engines include Java, Groovy, MiniLang (`simple`), generic scripts/JavaScript, automatic entity CRUD, service groups, interfaces, routes, JMS, and RMI. Most calls are local and synchronous; the service engine also supports asynchronous execution and persisted scheduled jobs. Service ECA rules can invoke follow-on services around another service's phases, and mail/calendar message ECA rules can translate inbound messages into service calls.

This service registry is the central integration mechanism between application modules. For example, order workflows call product/inventory, party, accounting/payment, shipment, and work-effort services by name. Because calls use the same dispatcher and delegator, they are method-like in latency and failure characteristics even though they have a service abstraction.

RMI, JMS, and route engines provide remote/integration options, but their presence does not make the current deployment distributed. The default runtime starts local engines and contains only example/commented remote locations except for the available RMI loader configuration.

### Web request and REST layers

Catalina discovers every `<webapp>` declared by active components and mounts it on the configured `default-server`. Back-office applications are separate servlet contexts—for example `/accounting`, `/ordermgr`, `/catalog`, `/facility`, `/partymgr`, `/workeffort`, `/manufacturing`, `/marketing`, `/sfa`, `/webtools`, and `/ofbizsetup`—but all are hosted by the same Tomcat and JVM.

Within a context, the OFBiz control servlet uses `controller.xml` to route a request to an event (Java, Groovy, service, or other handler) and then to a view. Authentication and authorization are enforced by controller rules, security services, and permission data. HTTP sessions and servlet context are local to the embedded container.

The `rest-api` component mounts `/rest` and registers REST services backed by the same service dispatcher and security/persistence layers. `/docs` serves generated API documentation assets. REST is another adapter into the monolith, not a separate server.

### Cross-cutting mechanisms

- **Security:** users, groups, permissions, and application security data are entities. Web routes and services declare authentication/permission requirements. Host-header restrictions and other web security settings are configuration-driven.
- **Transactions:** services and the entity engine share the framework transaction manager; service definitions control transaction scope and timeout.
- **Caching:** entity and other framework caches are in the application process. Cache clearing across nodes is not enabled in the default delegator.
- **Scheduling:** jobs are stored as entities and executed by the service engine's job manager inside the OFBiz runtime.
- **Configuration:** component defaults live beside code; files in root `config/` override selected runtime configuration. System properties and JVM arguments supplement file configuration.
- **Observability:** Log4j-based logs and Tomcat access logs go to `runtime/logs`; JSON/ECS-style logging can be enabled through `framework/base/config/debug.properties` and JVM properties assembled by Gradle.
- **Extension:** a plugin uses the same component descriptor and may add entities, services, web apps, libraries, data, and containers. It is incorporated into the same build and process.

## Data and initialization

Components declare data resources by reader name. The important sets are:

- `seed`: foundational reference data, types, permissions, and configuration;
- `seed-initial`: initial operational records such as scheduled services;
- `demo`: demonstration users and business data.

Gradle's `loadAll`/`ofbiz --load-data` paths start the special data-load container, create/update tables from entity metadata, and import component data in component order. Data loading is therefore coupled to the same model registry as runtime persistence. Multi-tenant loading tasks exist, but the normal default deployment is a single OFBiz process with default, OLAP, and tenant datasource groups.

Mutable state is intentionally outside compiled classes: local installations use `runtime/`; container installations persist `/ofbiz/runtime`, `/ofbiz/config`, and `/ofbiz/lib-extra` as volumes.

## Deployment architecture

### Local/development deployment

The standard lifecycle is:

1. initialize/use the Gradle wrapper with a JDK 17 installation;
2. run `./gradlew cleanAll loadAll` to compile, create the local H2 schemas, and load complete/demo data;
3. run `./gradlew ofbiz` to launch the root application with `Start`;
4. access embedded Tomcat, normally over HTTPS on port 8443 (HTTP 8080 is also configured).

The Gradle `ofbiz` task uses the main runtime classpath plus test classes for local administrative test execution. Runtime files and H2 data remain in the repository's `runtime/` directory.

### Archive/server deployment

`distTar` or `distZip` produces an application distribution in `build/distributions`. It contains launch scripts, the shared libraries/classpath, and the framework/application/theme/plugin component trees. After extraction, `bin/ofbiz` starts the same single-JVM architecture. The repository does not define a systemd unit, load balancer, external secret store, or production orchestration stack; those are operator concerns outside this checkout.

### Container deployment

The multi-stage `Dockerfile` is the concrete container packaging:

1. a Temurin 17 builder initializes Gradle and builds `generateSecretKeys distTar`;
2. a Temurin 17 runtime image extracts that distribution under `/ofbiz` and runs as the non-root `ofbiz` user;
3. `docker-entrypoint.sh` applies environment-driven configuration and one-time initialization before executing `bin/ofbiz`;
4. separate `runtime` and `demo` targets provide an empty/seedable image and an image preloaded with demo H2 data.

The image declares ports 8443 (HTTPS), 8009 (optional AJP), and 5005 (debugging). It declares volumes for `/ofbiz/runtime`, `/ofbiz/config`, `/ofbiz/lib-extra`, and initialization hooks. Hook directories allow scripts before/after configuration and data loading, plus extra XML data.

By default the container uses H2 within the runtime volume. If `OFBIZ_POSTGRES_HOST` is set, the entrypoint renders `config/entityengine.xml` from `docker/templates/postgres-entityengine.xml`, configures the main, OLAP, and tenant PostgreSQL databases, and obtains a PostgreSQL JDBC driver unless told not to. It also supports environment-driven admin credentials, seed/demo loading, allowed host headers, content URL prefix, component disabling, and AJP enablement. Marker files in `/ofbiz/runtime/container_state` make initialization idempotent for a persistent volume.

`docker/examples/postgres-demo/docker-compose.yml` is an example, not a production topology. It starts PostgreSQL plus one OFBiz container, initializes three databases/users, loads demo data, exposes 8443, persists logs, and uses an after-configuration hook (including Solr-related configuration). No Kubernetes, Terraform, Helm, or cloud deployment definitions are tracked in this repository.

### CI and image publication

GitHub Actions provides build and publication automation:

- `gradle.yml` runs checks and Javadoc with all upstream plugins pulled in, on JDK 17 and (for trunk) JDK 21 while still producing Java 17 bytecode;
- Sonar workflows analyze pull requests and branches;
- `docker-image.yml` builds `ghcr.io/apache/ofbiz` runtime, preloaded-demo, and framework-plus-plugins variants on trunk/release pushes and tags, and pushes only when the repository variable `DO_DOCKER_PUSH` is true;
- the repository does not contain a workflow that deploys a running OFBiz instance to an environment.

Thus CI produces checks, distributions indirectly through image construction, and container images. Runtime environment rollout is not represented here.

## Architectural boundaries and operational consequences

1. **One deployment unit.** A change in any active framework, application, theme, or plugin is built and normally released with the whole OFBiz runtime.
2. **Logical rather than isolated modules.** Components have descriptors and resource namespaces, but share classes, registries, databases, caches, transactions, and lifecycle.
3. **Shared relational model.** Domain integration is mostly entity relations and local service calls. There is no database-per-module ownership in the current configuration.
4. **Synchronous in-process coupling is common.** Named services make dependencies explicit at runtime, but most calls have the availability and latency characteristics of local calls, not resilient network calls.
5. **Metadata drives behavior.** XML component, entity, service, controller, widget, and ECA definitions are executable architecture and must be considered alongside Java/Groovy source.
6. **Horizontal scaling needs additional design.** Default entity cache invalidation is not distributed, sessions are local unless Tomcat clustering is configured, and scheduled jobs run in-process. The repository contains commented cluster options but no enabled multi-node topology.
7. **Configuration is partly source-coupled.** Non-container deployments use configuration shipped beside source/distribution. The container approach adds override files, environment variables, mounted libraries, and hooks, but still starts the same application.
8. **Optional remote mechanisms are not current service boundaries.** RMI, JMS, REST, and plugins are integration capabilities. Only REST web applications are enabled as ordinary adapters in the default main runtime; there is no checked-in microservice fleet.

## Primary architecture evidence

The description above is derived from these repository sources:

- component/build graph: `settings.gradle`, `common.gradle`, `build.gradle`, `framework/base/config/component-load.xml`, and the `component-load.xml` files under `framework/`, `applications/`, and `themes/`;
- module registrations: every active `ofbiz-component.xml` under those component roots;
- startup: `framework/start/src/main/java/org/apache/ofbiz/base/start/`, its resource properties, and `framework/base/src/main/java/org/apache/ofbiz/base/container/`;
- persistence: `framework/entity/config/entityengine.xml` and entity/model resources contributed by components;
- services: `framework/service/config/serviceengine.xml` and component `servicedef/` resources;
- web/runtime: `framework/catalina/ofbiz-component.xml`, `framework/webapp/`, application `WEB-INF/controller.xml` files, and `framework/rest-api/ofbiz-component.xml`;
- deployment: `README.md`, `DOCKER.adoc`, `Dockerfile`, `docker/docker-entrypoint.sh`, `docker/examples/postgres-demo/`, and `.github/workflows/`.
