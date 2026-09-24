# OFBiz current architecture

## Scope and executive summary

This document describes the architecture represented by the tracked source tree as inspected on 2026-09-23. It describes the framework distribution in this repository, not optional plugins that can be fetched separately and not generated or ignored local build artifacts.

Apache OFBiz is a **modular monolith**. Its framework and business applications are divided into components, but the standard runtime loads those components into one JVM and exposes their web applications through one embedded Tomcat instance. Components contribute definitions to shared, process-wide infrastructure:

- a combined entity model and common database abstraction (the Entity Engine);
- a combined service catalog and synchronous/asynchronous service dispatcher (the Service Engine);
- shared security, transaction, cache, scheduling, localization, and rendering facilities;
- multiple web applications mounted in the same servlet container.

The component boundary is consequently a source/configuration boundary, not a process, database-ownership, or network boundary. Business components call one another's services and read one another's entities directly. A normal deployment is one OFBiz application process plus a database, optionally placed behind a reverse proxy. The repository also supplies a Docker image and a PostgreSQL Compose example, but no Kubernetes, cloud infrastructure, service mesh, or independently deployable business services.

```text
Browser / API client
        |
        | HTTPS 8443 or HTTP 8080 by default
        v
+------------------------------------------------------------------+
| One OFBiz JVM                                                    |
|                                                                  |
| Embedded Tomcat                                                  |
|  /accounting /ordermgr /catalog /partymgr /rest ...              |
|       | controllers, events, screens, forms, FreeMarker          |
|       v                                                          |
| Shared Service Engine ---- scheduled jobs / ECA rules            |
|       | Java, Groovy, entity-auto, group and other engines       |
|       v                                                          |
| Shared Entity Engine ---- transactions / cache / ECA rules       |
|       |                                                          |
| Framework + application + theme + plugin components              |
+-------|----------------------------------------------------------+
        |
        | JDBC
        v
 H2 by default, or an external RDBMS (PostgreSQL is containerized)
```

## Repository and build structure

### Top-level layout

| Path | Responsibility |
| --- | --- |
| `framework/` | Reusable runtime kernel: startup, component loading, persistence, services, security, web, widgets, embedded Tomcat, testing, and administration. |
| `applications/` | ERP domain model and business applications. Each child is an OFBiz component. |
| `themes/` | Shared presentation resources and selectable UI themes, also packaged as components/web applications. |
| `plugins/` | Extension point for separately sourced or locally developed components. The directory is scanned even when empty. |
| `build.gradle`, `settings.gradle`, `common.gradle`, `dependencies.gradle` | Root Gradle build, dependencies, component discovery, packaging, tests, data loading, and developer tasks. |
| `buildSrc/` | Local Gradle convention logic, including Node conventions used by relevant UI assets. |
| `config/` | Repository-wide static-analysis configuration. Runtime configuration mainly remains within framework components. |
| `runtime/` | Mutable local runtime state: H2 data, logs, indexes, generated Catalina configuration, output, and temporary files. It is not application source. |
| `docker/`, `Dockerfile` | Multi-stage container build, entry point, runtime templates, and a PostgreSQL example. |
| `.github/workflows/` | Gradle verification and container-image CI pipelines. |

`settings.gradle` calls `activeComponents()` and includes each enabled component as a Gradle subproject. That same component metadata is used by the application runtime. There is therefore close alignment between build modules and runtime components, although the final distribution is assembled and launched as one application.

### Standard component anatomy

An OFBiz component is rooted at an `ofbiz-component.xml`. Depending on its role, it may contain:

- `src/main/java` and `src/main/groovy` implementation code;
- `config/` properties and engine configuration;
- `entitydef/` entity models, view entities, entity-group mappings, and Entity ECA rules;
- `servicedef/` service signatures, service groups, and Service ECA rules;
- `data/` seed, initial, demo, and upgrade XML data;
- `widget/` XML screen, form, menu, and tree definitions;
- `webapp/<name>/` static resources, templates, `WEB-INF/web.xml`, and OFBiz `controller.xml` routes;
- `testdef/` XML integration suites and conventional JVM tests.

The manifest registers these resources with named loaders. It can also register classpath entries, containers, web applications, keystores, and tests. Resource references such as `component://accounting/...` resolve through the component registry, allowing definitions in one component to reference resources in another.

### Component discovery and order

`framework/base/config/component-load.xml` scans these parent directories in order:

1. `framework`
2. `themes`
3. `applications`
4. `plugins`

Directories can provide a `component-load.xml` to impose a more specific order. `applications/component-load.xml`, for example, loads `datamodel`, `party`, `securityext`, `content`, `workeffort`, `product`, `manufacturing`, `accounting`, `humanres`, `order`, `marketing`, and finally `commonext`. This order supports model/resource availability, but should not be mistaken for strict dependency isolation: after startup the registries are shared.

## Framework modules

The framework components form the technical platform beneath every application:

| Component | Role |
| --- | --- |
| `base` | Bootstrap utilities, configuration/resource loading, component registry, caches, logging, crypto helpers, and the administrative shutdown server. |
| `start` | Command-line/JVM entry point. Selects startup loaders and starts registered containers. The normal loader is `main`. |
| `entity` | Entity Engine: metadata-driven ORM/query API, JDBC datasource abstraction, transaction participation, entity cache, delegators, and entity events. |
| `entityext` | Entity Engine extensions and the data-loader container used by seed/demo/import commands. |
| `service` | Service Engine: global service metadata, dispatch, validation, authentication flags, transactions, job scheduling, asynchronous execution, and Service ECA processing. |
| `security` | Authentication/authorization support and security configuration used by webapps and services. |
| `common` | Cross-cutting entities, services, utilities, and shared business primitives. |
| `webapp` | OFBiz servlet infrastructure, controller/event dispatch, sessions, request helpers, and URL handling. |
| `widget` | Metadata-driven screens, forms, menus, and trees plus renderers. |
| `catalina` | Embedded Apache Tomcat container and connector/webapp assembly. |
| `rest-api` | REST endpoint at `/rest` and API documentation webapp at `/docs`, backed by OFBiz services/security. |
| `webtools` | Administrative UI and operational tools, including entity/data and service tooling. |
| `datafile` | Structured flat-file parsing and writing. |
| `minilang` | Support for the legacy XML MiniLang implementation style still present in the codebase. |
| `testtools` | OFBiz XML test runner and test container. |

Startup is container-based. Component manifests register implementations such as the entity `DelegatorContainer`, the service container, the Catalina container, and the admin server. With the default `main` loader, startup builds the component registry, preloads the `default` delegator, initializes the service dispatcher, and starts Tomcat and the registered web applications. Alternative loaders support data loading, tests, and optional RMI operation.

## Business application modules

The application tier is organized by ERP capability rather than by independently owned services:

| Component | Principal responsibility and relationships |
| --- | --- |
| `datamodel` | Foundational ERP entity definitions for party, product, order, shipment, accounting, work effort, content, marketing, human resources, and manufacturing. It establishes the shared canonical data model. |
| `party` | People, organizations, roles, relationships, contact mechanisms, communication, and party profiles. Party identifiers and roles are referenced by almost every other domain. |
| `securityext` | Application-level security extensions and management data built on framework security and party/user-login concepts. |
| `content` | Content, documents, data resources, electronic text, and content management UI. Product, marketing, and other modules can associate content with their records. |
| `workeffort` | Tasks, projects, calendars, events, time tracking, and work-effort assignment. Human resources, manufacturing, services, and costing can reuse these concepts. |
| `product` | Product catalog, categories, pricing, inventory, facilities, shipment/facility operations, and related UIs. Order and manufacturing depend heavily on its entities and services. |
| `manufacturing` | Bills of material, routing/tasks, production runs, requirements, and material planning, integrating product/inventory and work-effort concepts. |
| `accounting` | General ledger, invoices, payments, billing accounts, tax, fixed assets, budgeting, and financial posting. It consumes order, product, party, and shipment facts and exposes accounting services back to them. |
| `humanres` | Employees, positions, employment, qualifications, skills, and HR administration, centered on parties and organizations. |
| `order` | Sales and purchase orders, carts, checkout, quotes, returns, requirements, requests, reservations, and shopping lists. It coordinates party, product, inventory/shipment, payment, and accounting behavior. |
| `marketing` | Campaigns, tracking, contact lists, segments, and sales-force automation using party, content, product, and order data. |
| `commonext` | Distribution-level setup and shared UI/JavaScript integration that needs visibility of most applications; it is deliberately loaded last. |

This table shows conceptual ownership, not exclusive data ownership. Entity definitions are merged, view entities join across domains, and service implementations sometimes live in a different component from the service definition that invokes them. These are important coupling points for migration planning.

## Presentation modules

`themes/common-theme` provides shared templates, macro libraries, images, login/error pages, and rendering support. `bluelight`, `flatgrey`, `helveticus`, `rainbowstone`, and `tomahawk` provide selectable visual themes and are registered as webapps. The presentation stack is primarily server-rendered:

1. A component webapp receives a servlet request.
2. `WEB-INF/controller.xml` maps the request URI to an event and a response.
3. An event may call a service or Java/Groovy handler.
4. A view map selects an OFBiz screen.
5. Screen definitions compose other screens, forms, menus, and actions.
6. Theme macros and FreeMarker templates render HTML (or another supported format).

Static JavaScript and CSS supplement this flow; the architecture is not a separate single-page frontend talking to a dedicated backend.

## Runtime interaction model

### Entity Engine and data access

Applications normally access data through a `Delegator`, particularly the preloaded `default` delegator. At startup, entity XML contributed by all enabled components becomes a single logical model. The Entity Engine supplies CRUD, dynamic queries, relationships, view entities, sequenced IDs, transaction integration, and caching.

The default `framework/entity/config/entityengine.xml` maps three entity groups through the delegator:

- `org.apache.ofbiz` for operational ERP data;
- `org.apache.ofbiz.olap` for analytics/OLAP data;
- `org.apache.ofbiz.tenant` for tenant metadata.

Out of the box these map to separate embedded H2 datasources (`localh2`, `localh2olap`, and `localh2tenant`). The configuration includes alternatives for several external databases. The Docker PostgreSQL template maps the same groups to distinct PostgreSQL database configurations. Database portability is provided by Entity Engine metadata and datasource adapters, although native SQL and database behavior still require normal migration scrutiny.

Entity ECA (event-condition-action) rules can react to create, update, remove, or other entity operations. This permits cross-cutting automation without an explicit call at the original write site, but it also makes runtime dependency tracing less obvious.

### Service Engine and business orchestration

Services are declared in XML with typed input/output attributes, authentication and transaction settings, and an implementation engine. The global dispatcher can invoke services synchronously, asynchronously, or as scheduled jobs. Common implementation styles include:

- Java static methods;
- Groovy scripts/classes;
- `entity-auto` generated CRUD operations;
- grouped services that compose other services;
- interface and route/remote-oriented engines;
- legacy MiniLang where retained.

Calls generally stay inside the process and share transaction context. Services routinely invoke services declared by other business components. Service ECA rules run additional services around service lifecycle events; scheduled-job records cause services to be executed by the job manager. Optional SSL RMI dispatcher configuration exists, but it is registered only for the `rmi` loader and is not part of the standard `main` deployment.

### Web and API interaction

Each `<webapp>` in a component manifest is mounted by the Catalina container on the declared path, for example `/accounting`, `/ap`, `/ar`, `/ordermgr`, `/catalog`, `/facility`, `/partymgr`, `/humanres`, `/manufacturing`, `/marketing`, `/workeffort`, `/webtools`, `/rest`, and `/docs`. These contexts use the same JVM registries and normally the same login/security domain.

Traditional web controllers can invoke Service Engine services directly. The REST component exposes API behavior over HTTP while reusing the same service metadata, authentication/authorization, and entity-backed implementations. It is an additional adapter into the monolith, not a separate API process.

### Typical cross-module transaction

An order checkout illustrates the coupling:

1. An order web controller accepts a user action and invokes a checkout/order service.
2. Order code reads party, product, price, facility, and inventory entities through the shared delegator.
3. The dispatcher calls inventory, shipment, payment, tax, notification, and accounting-related services as needed.
4. Service or Entity ECA rules may trigger further work.
5. All synchronous steps can participate in the same database transaction.
6. Later work can be placed in the shared job scheduler.
7. The controller renders an OFBiz widget screen through the selected theme.

There is no message broker in the default architecture and no network boundary between these domains. Asynchrony is principally the database-backed Service Engine job mechanism.

## Configuration, extension, and state

Configuration is conventionally distributed across component `config/` directories and resolved from the classpath. Important examples include Entity Engine datasources, security properties, URL settings, logging, cache settings, and service-engine behavior. A custom plugin can add entities, services, webapps, data, screens, or classpath configuration without modifying core components.

The main categories of mutable state are:

- relational data in H2 or an external database;
- runtime logs;
- search/index data under `runtime/indexes` where enabled;
- generated Catalina/runtime files, output, and temporary data;
- container initialization markers when the Docker entry point is used.

Multi-tenancy is represented by tenant metadata and tenant-specific delegator names such as `default#<tenantId>`. It remains an application/data-layer facility inside the same runtime unless operators deploy separate instances.

## Build and packaging

The Gradle wrapper is the primary build interface. The root build dynamically includes enabled components, compiles Java and Groovy, collects resources, runs static analysis/tests, supports data-loading tasks, and produces distributions. A distribution contains launcher scripts (`bin/ofbiz`), framework/application resources, libraries, and configuration needed to run the monolith.

The source/target compatibility is Java 17. CI verifies the build on JDK 17 and, on trunk, JDK 21 as an additional runtime compatibility check. The normal CI build fetches the separately maintained plugin set before running `check` and Javadoc; those plugins are not intrinsic to the framework-only architecture documented here.

## Deployment architecture

### Direct JVM deployment

The simplest deployment builds or unpacks the distribution and runs `bin/ofbiz` (or the corresponding Gradle run task during development). One OS process hosts the complete component set and embedded Tomcat. The Catalina component defines:

- HTTPS on port `8443`;
- HTTP on port `8080`;
- AJP on port `8009` (bound according to Tomcat defaults unless explicitly opened more broadly);
- an admin shutdown endpoint configured on loopback port `10523`;
- optional, commented Tomcat clustering/session-replication settings.

Production operators normally provide an external RDBMS, certificates/secrets, durable runtime directories, and often a reverse proxy/load balancer. The repository does not prescribe that edge tier. Horizontal scaling is not automatic: shared database access, cache invalidation settings, sessions, scheduled jobs, filesystem state, and singleton-style work must be designed for multiple OFBiz nodes. The default delegator explicitly has distributed cache clearing disabled, and Tomcat clustering is only an inactive example.

### Container image

The root `Dockerfile` builds two Java 17 Temurin-based targets:

- `runtime`: the distribution with no data preloaded;
- `demo`: the same runtime with demo data loaded during image construction.

The builder runs Gradle `generateSecretKeys` and `distTar`; the runtime stage extracts that archive under `/ofbiz`, runs as the unprivileged `ofbiz` user, and starts `/ofbiz/bin/ofbiz` through `docker/docker-entrypoint.sh`. The image declares `8443` (HTTPS), `8009` (AJP), and `5005` (debugging) as exposed ports. The HTTP connector still listens on `8080` inside the container but that port is not declared with `EXPOSE` or published by the supplied Compose example. Declaring `EXPOSE` is metadata rather than host publication; operators must explicitly publish the desired ports.

The following mount points separate persistent/configurable data from the image:

- `/ofbiz/config` for classpath-precedence configuration overrides;
- `/ofbiz/runtime` for database/runtime state and initialization markers;
- `/ofbiz/lib-extra` for additional JDBC libraries;
- `/docker-entrypoint-hooks` for initialization customization.

On first start, the entry point can:

1. generate a PostgreSQL Entity Engine override from environment variables;
2. download the PostgreSQL JDBC driver unless disabled;
3. override allowed hosts and content URL prefixes;
4. enable AJP and disable selected components;
5. load no data, seed data, or demo data;
6. create/update the administrator login;
7. load extra XML data and execute before/after hook scripts.

Marker files under `/ofbiz/runtime/container_state` make these initialization phases idempotent for a persistent volume. Configuration/data changes after first initialization therefore require deliberate marker/volume management rather than merely changing an environment variable.

### Supplied PostgreSQL topology

`docker/examples/postgres-demo/docker-compose.yml` demonstrates two containers:

```text
host :8443 ---> OFBiz container ---- JDBC ----> PostgreSQL container
                    |
                    +-- mounted runtime logs
                    +-- post-configuration hook
```

The Compose file publishes only HTTPS 8443, loads demo data, configures the public host/content URL, and supplies PostgreSQL settings through environment files. PostgreSQL initialization creates the operational, OLAP, and tenant databases/users expected by the template. This is a development/demo example: it sets resource limits and has no production ingress, TLS-secret management, database backup/high availability, orchestration, or observability stack.

### CI/CD boundary

GitHub Actions builds/tests the Gradle project and has a separate image workflow. The image workflow creates framework runtime, preloaded-demo, and framework-plus-plugins variants and can publish them to `ghcr.io/apache/ofbiz` when repository configuration permits. It builds artifacts; it does not deploy a running environment. No environment promotion or infrastructure-as-code deployment is present in this repository.

## Architectural characteristics and migration implications

### Strengths

- Metadata-driven entity, service, web, and widget layers provide consistent conventions across a broad ERP suite.
- A single process makes cross-domain calls and ACID transactions straightforward.
- Components and plugins offer meaningful packaging and extension points without requiring distributed infrastructure.
- Entity Engine abstracts several relational databases; Service Engine centralizes validation, transactions, scheduling, and security integration.
- The distribution can run directly or as a reproducible container image.

### Constraints and coupling hotspots

- Component boundaries are porous: shared tables, global metadata registries, cross-component resource references, direct entity reads, and in-process service calls prevent independent deployment.
- A single canonical database model makes per-module schema ownership unclear. View entities and cross-domain transactions increase extraction difficulty.
- ECA rules, scheduled services, and controller-driven orchestration create implicit call paths beyond ordinary Java/Groovy references.
- UI, workflow, services, and persistence definitions for a capability are spread across XML, Java/Groovy, templates, and seed data.
- Shared caches, sessions, filesystem state, and scheduler behavior require explicit work before safe horizontal scaling.
- The REST layer exposes the existing service/application model; it does not itself establish stable bounded-context APIs.
- Default H2 and demo/container conveniences are suitable for evaluation, not a production operational design.

For migration analysis, the effective dependency graph should therefore be derived from at least: entity ownership and foreign relationships, view entities, service-to-service calls, service/entity ECA rules, scheduled-service seed data, `component://` references, controller events, and shared widget/template usage. Directory boundaries alone substantially understate runtime coupling.

## Key source references

The principal files supporting this description are:

- `framework/base/config/component-load.xml` and `applications/component-load.xml` — discovery and application load order;
- every component's `ofbiz-component.xml` — resources, containers, webapps, and mount points;
- `settings.gradle` and the root `build.gradle` — dynamic Gradle modules, compilation, testing, data, and packaging;
- `framework/entity/config/entityengine.xml` — delegators, groups, and datasource choices;
- `framework/entity/ofbiz-component.xml` and `framework/service/ofbiz-component.xml` — core runtime containers;
- `framework/catalina/ofbiz-component.xml` — embedded Tomcat, connectors, and webapp host;
- application `entitydef/`, `servicedef/`, `widget/`, and `webapp/*/WEB-INF/controller.xml` directories — domain implementation and request flows;
- `Dockerfile`, `docker/docker-entrypoint.sh`, and `docker/templates/postgres-entityengine.xml` — container packaging and initialization;
- `docker/examples/postgres-demo/docker-compose.yml` — supplied two-container example;
- `.github/workflows/gradle.yml` and `.github/workflows/docker-image.yml` — build and image publication pipelines.
