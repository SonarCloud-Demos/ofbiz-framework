## 4. PCI-DSS v4.0 (Requirement 6: Secure Systems & Software)

### Rule 4.1: Prohibition of Credit Card PAN/CVV Logging (PCI 3.3 / 6.2)
* **Severity:** CRITICAL
* **Description:** Primary Account Numbers (PAN) and CVV security codes must never be stored in plaintext logs, caches, or temporary storage.
* **Detection:** Scan changes writing request parameters or objects containing payment card strings to application logs or local disk.
* **Required Fix:** Apply masking functions to expose only the last 4 digits of card numbers, and strip CVV fields entirely.

### Rule 4.2: Cross-Site Scripting (XSS) Prevention (PCI 6.2.4)
* **Severity:** HIGH
* **Description:** User input rendered in web interfaces or screen widgets must be contextually encoded or sanitized to prevent script execution.
* **Detection:** Detect unescaped FreeMarker variables (`${...}`) or raw HTML assignments handling user input without encoding.
* **Required Fix:** Apply FreeMarker escaping built-ins (`?html`) or use OFBiz encoder utilities (`StringUtil.htmlEncoder`).

### Rule 4.3: Insecure Deserialization Safeguards (PCI 6.2.4)
* **Severity:** CRITICAL
* **Description:** Untrusted input processed through dynamic Java object deserializers can lead to Remote Code Execution (RCE).
* **Detection:** Flag unsafe Java deserialization calls (`ObjectInputStream.readObject()`, `XMLDecoder`).
* **Required Fix:** Replace dynamic deserializers with safe, strict JSON/XML parsing alternatives.

### Rule 4.4: Server-Side Request Forgery (SSRF) Prevention (PCI 6.2.4)
* **Severity:** HIGH
* **Description:** External requests initiated based on user input must be validated against strict domain allowlists.
* **Detection:** Identify outbound HTTP requests constructed using dynamic user-supplied URLs without prior validation.
* **Required Fix:** Add validation logic comparing the requested target URL against an explicit domain allowlist before issuing the request.
