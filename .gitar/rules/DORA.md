## 3. DORA (Digital Operational Resilience Act - EU)

### Rule 3.1: External Integration Circuit Breakers & Timeouts (DORA Resilience)
* **Severity:** HIGH
* **Description:** Outbound integration calls to external services (e.g., credit check providers, rating engines) must feature explicit timeouts and fault tolerance.
* **Detection:** Identify external HTTP/RPC requests in OFBiz Java services missing socket/connect timeout parameters or retry/circuit-breaker logic.
* **Required Fix:** Wrap outbound calls with strict timeout configurations (e.g., maximum 3000ms) and circuit-breaker wrappers (e.g., Resilience4j).

### Rule 3.2: Async Exception Handling in Service Engine (DORA Resilience)
* **Severity:** HIGH
* **Description:** Asynchronous tasks and job scheduler executions must not fail silently or crash background processes.
* **Detection:** Flag unhandled exceptions or empty `catch` blocks in OFBiz Java service execution contexts.
* **Required Fix:** Inject explicit `try/catch` blocks returning `ServiceUtil.returnError()` with structured error logging.

### Rule 3.3: Delegator Transaction & Connection Leak Prevention (DORA Resilience)
* **Severity:** HIGH
* **Description:** Database connections, transaction contexts, and entity engine iterators must be explicitly closed to prevent resource exhaustion.
* **Detection:** Detect `EntityListIterator` or `SQLProcessor` instances lacking deterministic closing in a `finally` block or `try-with-resources`.
* **Required Fix:** Wrap database iterators and connection objects in `try-with-resources` or explicit `finally` cleanup calls.

### Rule 3.4: Liveness/Readiness Probes & Graceful Shutdown (DORA Resilience)
* **Severity:** MEDIUM
* **Description:** Microservices and web application containers must support operational monitoring and clean process termination.
* **Detection:** Flag container configurations or web components missing health check ping handlers.
* **Required Fix:** Add standardized health check services (`/healthz`) and graceful process shutdown hooks.