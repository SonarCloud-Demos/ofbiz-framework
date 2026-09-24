## 2. NAIC Insurance Data Security Model Law

### Rule 2.1: Log Sanitization of Policyholder NPI/PII (NAIC Section 4)
* **Severity:** CRITICAL
* **Description:** Policyholder Nonpublic Information (SSN, Tax ID, birthdates, claims details, health data) must never be written to plaintext logs.
* **Detection:** Scan `Debug.logInfo()`, `Debug.logError()`, or `logger.info()` calls for un-redacted objects containing sensitive insurance attributes.
* **Required Fix:** Wrap logged payload variables with a field-masking or PII-redaction utility prior to log output.

### Rule 2.2: Entity Field-Level Data Encryption at Rest (NAIC Section 4)
* **Severity:** CRITICAL
* **Description:** Sensitive policyholder data fields stored in persistent databases must utilize field-level encryption.
* **Detection:** Flag OFBiz entity definitions (`entitymodel.xml`) or Java entity models containing PII attributes missing `encrypt="true"` or field-level encryption attributes.
* **Required Fix:** Apply field-level encryption attributes (`encrypt="true"`) to the target entity fields.

### Rule 2.3: Insecure Direct Object Reference (IDOR) Mitigation (NAIC Section 4)
* **Severity:** HIGH
* **Description:** Access to policy records, quotes, or claims details must verify ownership against the active user login.
* **Detection:** Inspect OFBiz Java service methods fetching records by `policyId` or `claimId` that lack an explicit userLogin association check.
* **Required Fix:** Inject ownership validation checks verifying that `userLogin.userLoginId` matches the record owner's ID.

### Rule 2.4: Enforce Modern Transport Security (NAIC Section 4)
* **Severity:** HIGH
* **Description:** Outbound HTTP/gRPC network calls must enforce modern TLS configurations.
* **Detection:** Flag Java HTTP client configurations allowing unencrypted HTTP fallback, disabled certificate validation (`TrustAllStrategy`), or TLS versions lower than TLS 1.2.
* **Required Fix:** Enforce certificate validation and configure minimum transport protocols to TLS 1.3.