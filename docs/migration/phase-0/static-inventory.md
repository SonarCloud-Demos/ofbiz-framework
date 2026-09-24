# Phase 0 static technical inventory

## Scope and method

This inventory was produced from tracked `framework/`, `applications/`, and `themes/` sources on 2026-09-23. Counts are directional discovery metrics, not API commitments: XML test fixtures and framework definitions can affect broad text counts, overloaded terms can create false positives, and inactive definitions remain present in source. Runtime telemetry must identify what production actually uses.

Optional plugins, untracked local experiments, deployed configuration overrides, production database contents, and external infrastructure are outside this source-only inventory.

## Size of the migration surface

| Asset | Count | Migration significance |
| --- | ---: | --- |
| Production component manifests | 12 application, 14 framework, 6 theme | Build modules share a single runtime; manifests are not deployment boundaries |
| Entity definition XML files | 31 | Models are composed into one Entity Engine registry |
| Concrete entity declarations | 846 | Candidate data ownership requires relationship and usage analysis |
| View-entity declarations | 274 | Often encode cross-domain joins that must become APIs, projections, or reports |
| Service definition XML files | 130 | Services share one global dispatcher/catalog |
| Service declarations | 3,912 | Includes framework and application definitions, active and inactive |
| Controller XML files | 16 | Each contains many user/API routes |
| Request maps | 3,127 | Starting point for user-journey and authorization inventory |
| Widget XML files | 391 | UI behavior is distributed across screens/forms/menus/trees |
| Service ECA rules | 330 | Implicit behavior around service execution |
| Entity ECA rules | 35 | Implicit behavior around writes/reads depending on rule |
| XML test-definition files | 66 | Characterization assets to preserve and refine |
| `component://` references | 7,121 | Includes same-component references; cross-component subset evidences coupling |

The codebase also contains approximately 1,130 main Java and 441 main Groovy source files under the inventoried roots. Exact language counts are less useful than runtime call paths because a large part of the behavior is declared in XML.

## Application component profile

Entity counts below represent definitions physically in each application directory. The apparent zeros are important: most concrete domain entities live centrally in `datamodel`, not beside their behavior.

| Component | Concrete entities | View entities | Services | Controller routes | Service ECAs | Entity ECAs | Component references |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| accounting | 0 | 3 | 799 | 552 | 69 | 3 | 1,430 |
| commonext | 0 | 0 | 8 | 36 | 0 | 0 | 82 |
| content | 0 | 0 | 310 | 347 | 26 | 11 | 521 |
| datamodel | 733 | 160 | 0 | 0 | 0 | 0 | 51 |
| humanres | 0 | 0 | 174 | 232 | 0 | 0 | 368 |
| manufacturing | 0 | 0 | 184 | 155 | 6 | 0 | 339 |
| marketing | 0 | 0 | 151 | 146 | 10 | 0 | 400 |
| order | 0 | 53 | 488 | 378 | 110 | 3 | 804 |
| party | 0 | 0 | 289 | 275 | 27 | 0 | 625 |
| product | 0 | 4 | 875 | 764 | 56 | 14 | 1,229 |
| securityext | 0 | 0 | 2 | 0 | 0 | 0 | 4 |
| workeffort | 0 | 33 | 177 | 96 | 16 | 4 | 580 |

“Component references” includes self-references and is a complexity indicator, not a count of external dependencies. High self-reference volume shows how extensively component-resource indirection is used.

## Central data model by domain file

| Datamodel file | Concrete entities | View entities |
| --- | ---: | ---: |
| accounting | 147 | 37 |
| content | 64 | 13 |
| human resources | 41 | 5 |
| manufacturing | 8 | 1 |
| marketing | 32 | 10 |
| order | 112 | 2 |
| party | 84 | 41 |
| product | 169 | 39 |
| shipment | 35 | 12 |
| work effort | 41 | 0 |

These files account for 733 concrete entities; framework components and application extensions provide the remainder. File grouping is a useful vocabulary seed but does not prove aggregate or service ownership. Relations and view aliases cross these groupings.

## Service implementation styles

| Engine | Declarations | Migration treatment |
| --- | ---: | --- |
| `entity-auto` | 1,860 | Identify actual CRUD use; do not expose generic table CRUD as target APIs |
| Java | 628 | Characterize business behavior and transaction assumptions |
| Groovy | 605 | Characterize and port by capability, not file |
| MiniLang/simple | 305 | Preserve behavior with characterization tests before replacement |
| interface | 62 | Candidate contract hints, not automatically service APIs |
| group | 37 | Reveal orchestration and transaction boundaries |
| SOAP/JMS/HTTP/RMI/route combined | 27 | Verify deployed endpoints/consumers and external contracts |
| other script engines | 9 | Confirm whether active before planning replacement |

The total above includes definition variants and interface declarations; it supports sizing, not a one-to-one target service mapping.

## User-facing web applications

| Capability | Mount points | Manifest base permission |
| --- | --- | --- |
| Accounting | `/accounting`, `/ar`, `/ap` | `OFBTOOLS,ACCOUNTING` |
| Setup/shared JS | `/ofbizsetup`, `/common-js`, `/ordermgr-js` | `OFBTOOLS,SETUP` where applicable |
| Content | `/content`, `/contentimages/` | `OFBTOOLS,CONTENTMGR` |
| Party | `/partymgr` | `OFBTOOLS,PARTYMGR` |
| Product/facility | `/catalog`, `/facility` | `OFBTOOLS,CATALOG` / `OFBTOOLS,FACILITY` |
| Human resources | `/humanres` | `OFBTOOLS,HUMANRES` |
| Work effort/calendar | `/workeffort`, `/iCalendar` | `OFBTOOLS,WORKEFFORTMGR` where applicable |
| Orders | `/ordermgr` | `OFBTOOLS,ORDERMGR` |
| Manufacturing | `/manufacturing` | `OFBTOOLS,MANUFACTURING` |
| Marketing/SFA | `/marketing`, `/sfa` | `OFBTOOLS,MARKETING` / `OFBTOOLS,SFA` |
| Framework administration/API | `/webtools`, `/rest`, `/docs` | `OFBTOOLS,WEBTOOLS` for webtools; REST has its own configuration |
| Themes/assets | `/common`, `/images`, and theme mount points | Shared presentation resources |

Controller-level security, service `auth` flags, permission services, entity permissions, role checks, and data scoping must be added to the authorization matrix. A base permission alone is insufficient to reproduce target authorization.

## Asynchronous, scheduled, and implicit behavior

Production-relevant scheduled-service data is visibly contributed by:

- `framework/service`, `framework/entityext`, and `framework/webtools`;
- `applications/accounting`;
- `applications/order`;
- `applications/product`;
- `applications/manufacturing`.

There are also temporal/job entities and demo/test schedules. Phase 0 must query the deployed `JobSandbox`, recurrence, and runtime records to distinguish configured, disabled, transient, and actively recurring work. For every active item capture service, parameters, schedule/time zone, concurrency behavior, retry policy, owner, business deadline, downstream effects, and last successful execution.

The ECA inventory must be expanded into a graph of trigger, condition, invoked service, transaction phase, and error semantics. This is required before moving a writer because an apparently local entity or service operation may trigger another domain.

## Initial coupling observations

- `common` is a broad shared dependency across all applications.
- Accounting references party, product, order, work effort, and common resources in addition to its own resources.
- Order references party, product, work effort, accounting, and common resources and has the highest application Service ECA count.
- Product combines catalog, facility, inventory, pricing, promotions, shipment, subscription, supplier, rental, configuration, and costing behaviors in one component. It is several candidate bounded contexts.
- Party code references accounting, order, HR, work effort, and product; the target party context must not absorb all these domains merely because current screens compose them.
- Marketing is strongly connected to party and also uses order, product, and work-effort behavior.
- Work effort has numerous cross-domain views/references and should be separated from HR/manufacturing only after use-case workshops.
- `datamodel` references optional ecommerce resources, showing that the deployed plugin set can materially change the graph.

## External integration discovery

Source declares service engines for SOAP, JMS, HTTP, RMI, and routes and includes concepts/configuration for email, payment methods/providers, FTP addresses, shipping gateways, and calendar access. This proves capability, not production use. Complete an integration register with:

- external system and business owner;
- direction, transport, endpoint, authentication/certificate, IP allowlisting, and data classification;
- invoking route/service/job and source code/configuration location;
- volumes, peak rate, timeout/retry/idempotency behavior, availability contract, and support hours;
- test/sandbox availability and contractual change windows;
- target disposition: retain through adapter, replace, event integration, or retire.

Do not place credentials, production endpoints, customer data, or certificate material in this repository inventory.

## Runtime evidence still required

- component/plugin list and exact deployed revisions/configuration overrides;
- access-log route frequency, latency, status, payload/upload size, and authenticated role/tenant aggregates;
- service dispatcher traces including nesting, duration, transaction boundary, failure, and async submission;
- entity/table read/write frequency and top cross-domain joins without collecting row values;
- active ECA firings and scheduled jobs;
- database table sizes, row counts, change rates, key quality, orphan/duplicate rates, and tenant distribution;
- outbound destinations and inbound consumers from approved network/application telemetry;
- report catalog and business-critical spreadsheet/manual processes;
- incidents, slow queries, batch overruns, recovery history, and operational runbooks.

Instrumentation and queries must be reviewed for performance and privacy before use in production.
