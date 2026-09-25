## 1. NYDFS 23 NYCRR 500 (Financial & Insurance Cyber Security)

### Rule 1.1: Zero Hardcoded Secrets (NYDFS 500.07)
* **Severity:** CRITICAL
* **Description:** Hardcoded credentials, private keys, database connection strings, and API tokens are strictly prohibited in Java source code or OFBiz configuration properties.
* **Detection:** Scan all added or modified Java lines or `.properties` files for string literals matching API keys, passwords, bearer tokens, or private keys.
* **Required Fix:** Refactor code to fetch credentials dynamically from system environment variables or enterprise secret vaults (`org.apache.ofbiz.base.util.EntityUtilProperties` or HashiCorp Vault integrations).

### Rule 1.2: Enforce Service Permission Checks (NYDFS 500.07)
* **Severity:** HIGH
* **Description:** Administrative and policy modification services in OFBiz must enforce explicit security permission evaluations.
* **Detection:** Flag OFBiz Java service methods or event handlers updating policy, claim, or user data that lack `security.hasEntityPermission()` or `@AuthCheck` validations.
* **Required Fix:** Inject explicit permission checks validating user authorizations prior to executing business logic.

### Rule 1.3: Prevent Raw SQL Injections in Entity Engine (NYDFS 500.08)
* **Severity:** CRITICAL
* **Description:** Direct string concatenations inside `GenericDelegator.findListIteratorByCondition()` or raw `SQLProcessor` executions bypass OFBiz ORM security.
* **Detection:** Detect un-sanitized string concatenations passed to `SQLProcessor.prepareStatement()` or dynamic SQL queries.
* **Required Fix:** Convert raw SQL strings to standard OFBiz EntityEngine `EntityCondition` objects or parameterized `SQLProcessor` prepared statements.

### Rule 1.4: Input Validation at Service Boundaries (NYDFS 500.08)
* **Severity:** HIGH
* **Description:** All incoming HTTP event parameters and service context maps must be validated against strict schemas before processing.
* **Detection:** Flag OFBiz Java event methods (`HttpServletRequest` / `HttpServletResponse`) accessing unvalidated request parameters directly.
* **Required Fix:** Enforce validation routines or XML service context constraints (`IN` parameters) before processing payload data.

### Rule 1.5: Secure Web Session Flags (NYDFS 500.08)
* **Severity:** HIGH
* **Description:** Cookies holding authentication tokens or session IDs (`JSESSIONID`) must enforce secure transport flags in server configurations.
* **Detection:** Scan cookie creation logic or web manifold setups missing `HttpOnly`, `Secure`, or `SameSite` flags.
* **Required Fix:** Explicitly set cookie properties to mandate `HttpOnly=true`, `Secure=true`, and `SameSite=Strict`.

### Rule 1.6: Deprecated Cryptography Elimination (NYDFS 500.15)
* **Severity:** CRITICAL
* **Description:** Weak or broken cryptographic algorithms (MD5, SHA-1, DES) are strictly prohibited for Nonpublic Information (NPI).
* **Detection:** Flag usages of `java.security.MessageDigest.getInstance("MD5")` or deprecated cipher primitives.
* **Required Fix:** Upgrade cryptographic implementations to AES-256-GCM, SHA-256, or Argon2id/bcrypt for password hashing.