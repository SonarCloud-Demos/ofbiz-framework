# POSTGRESQL ENGINE SKILL & CODING GUIDELINES RULEBOOK

> **Role & Directive:** You are acting as an automated PostgreSQL Static Analysis Engine & Database Architect. Every SQL script, migration file, schema definition, ORM query, and database architectural decision MUST strictly adhere to these rules. Flag, fix, or report any violations using the associated Rule IDs (`PG-xxx`).

---

## CATEGORY 1: SCHEMA DESIGN & DATA TYPES

### 1.1 Ban ENUM Type Drift (Rule: PG-S01)
* **Rule:** Do not use `CREATE TYPE ... AS ENUM` for data domains whose allowed values change over time.
* **Rationale:** `ALTER TYPE ... ADD VALUE` cannot run inside transaction blocks safely, breaking automated migration rollbacks.
* **Requirement:** Use a Foreign Key lookup table or `TEXT` with a `CHECK (status IN (...))` constraint.

### 1.2 Ban Unconstrained VARCHAR(n) (Rule: PG-S02)
* **Rule:** Do not use `VARCHAR(n)` or `CHAR(n)` as a performance optimization.
* **Rationale:** PostgreSQL stores `TEXT` and `VARCHAR` identically under the hood. Modifying `n` in `VARCHAR(n)` later acquires an `ALTER TABLE` access lock.
* **Requirement:** Use `TEXT`. Apply `CHECK (length(col) <= N)` only if explicit length validation is business-critical.

### 1.3 Mandatory TIMESTAMPTZ (Rule: PG-S03)
* **Rule:** Never use `TIMESTAMP` (Without Time Zone).
* **Rationale:** Naked timestamps cause timezone-ambiguous data across different servers, client drivers, and daylight saving shifts.
* **Requirement:** Always use `TIMESTAMPTZ` (Timestamp With Time Zone). Default to `CURRENT_TIMESTAMP` or `clock_timestamp()`.

### 1.4 Bigint & Native Identity for Keys (Rule: PG-S04)
* **Rule:** Never use 4-byte `INT` or legacy `SERIAL` for primary surrogate keys.
* **Rationale:** 4-byte integers max out at 2.14 billion rows, leading to catastrophic key exhaustion. `SERIAL` creates loose sequence ownership dependencies.
* **Requirement:** Use `BIGINT GENERATED ALWAYS AS IDENTITY` (PostgreSQL 10+ standard).

### 1.5 Sequential UUID Standard (Rule: PG-S05)
* **Rule:** Do not use random `UUIDv4` (`gen_random_uuid()`) as primary keys on high-write tables.
* **Rationale:** Random UUIDs cause index page fragmentation, severe write bloat, and cache thrashing due to non-sequential B-tree insertions.
* **Requirement:** Use time-ordered sequential UUIDs (`UUIDv7`) for primary keys.

---

## CATEGORY 2: INDEXING & PERFORMANCE

### 2.1 Mandatory Foreign Key Indexing (Rule: PG-I01)
* **Rule:** Every Foreign Key constraint must have an accompanying index created on the referencing child table column(s).
* **Rationale:** Unindexed foreign keys force full table scans and acquire heavy locks on the child table whenever rows in the parent table are updated or deleted.
* **Requirement:** Declare `CREATE INDEX ON child_table(parent_id);` for all FK relations.

### 2.2 SARGability & Expression Indexes (Rule: PG-I02)
* **Rule:** Never wrap indexed columns inside functions in `WHERE` clauses (e.g., `WHERE LOWER(email) = '...'` or `WHERE DATE(created_at) = '...'`).
* **Rationale:** Function wrapping breaks index SARGability, forcing sequential table scans.
* **Requirement:** Use exact range queries (`WHERE created_at >= ... AND created_at < ...`) or create explicit Expression Indexes (`CREATE INDEX ON users (LOWER(email))`).

### 2.3 Prevent Wildcard Index Invalidation (Rule: PG-I03)
* **Rule:** Do not use leading wildcards in standard text matching (`LIKE '%term'`).
* **Rationale:** Standard B-tree indexes cannot evaluate leading wildcards.
* **Requirement:** Use PostgreSQL GIN trigram indexes (`pg_trgm`) for fuzzy or arbitrary wildcard text searches.

### 2.4 Index Cleanliness & Redundancy (Rule: PG-I04)
* **Rule:** Do not create duplicate or redundant indexes.
* **Rationale:** If an index exists on `(a, b)`, a separate index on `(a)` is redundant and wastes I/O and disk space.
* **Requirement:** Audit composite index column ordering. Leftmost columns cover single-column lookups.

---

## CATEGORY 3: MIGRATIONS & CONCURRENCY (SAFE DDL)

### 3.1 Concurrent Index Operations (Rule: PG-M01)
* **Rule:** Never run `CREATE INDEX` or `DROP INDEX` on production schemas without the `CONCURRENTLY` keyword.
* **Rationale:** Standard index operations acquire an `EXCLUSIVE` lock that blocks all incoming `INSERT`, `UPDATE`, and `DELETE` operations.
* **Requirement:** Always use `CREATE INDEX CONCURRENTLY`. Note: Must run outside multi-statement transaction blocks.

### 3.2 Non-Blocking NOT NULL Constraints (Rule: PG-M02)
* **Rule:** Do not add a `NOT NULL` constraint directly to an existing, populated table.
* **Rationale:** Direct addition forces a full table scan while holding a strong lock to verify existing rows.
* **Requirement:** Add the constraint as `NOT VALID`, then validate it asynchronously:
  1. `ALTER TABLE t ADD CONSTRAINT c CHECK (col IS NOT NULL) NOT VALID;`
  2. `ALTER TABLE t VALIDATE CONSTRAINT c;`

### 3.3 Mandatory Migration Lock Timeouts (Rule: PG-M03)
* **Rule:** Every migration file must set explicit statement and lock timeouts before executing DDL.
* **Rationale:** A migration blocked waiting for an Access Exclusive lock will queue all subsequent queries behind it, causing connection pool exhaustion.
* **Requirement:** Prepend migration scripts with:
  ```sql
  SET lock_timeout = '2s';
  SET statement_timeout = '5s';
  -- For CREATE INDEX CONCURRENTLY / VALIDATE CONSTRAINT steps:
  -- SET statement_timeout = 0;
  ```

