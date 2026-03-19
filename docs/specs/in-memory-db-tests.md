# Feature Spec: In-Memory Database Integration Tests

## Overview
A test suite using H2 (MySQL compatibility mode) as an in-memory database to unit-test
database service layer logic without requiring a live MySQL instance.

### Goals
- Fast, isolated tests that run without external dependencies
- Full coverage of CRUD operations and business logic in DB service classes
- Validate Kotlin Exposed DSL queries, transactions, and error handling

### Non-Goals
- Testing MySQL-specific features not supported by H2 (e.g. full-text search, JSON functions)
- End-to-end or contract tests against a real MySQL instance (those live in `/src/integrationTest`)
- Testing Spring Data JPA repositories (we use Exposed directly)

---

## What to Test
We are only going to test the service file functions located in `com.goldberg.law.database.service`.
There should not be any need for the tests to interact with any of the Exposed table or entity classes.

### Generic Testing Strategy
For each DB service class, cover (if the functions exist or is applicable):

1. **Full CRUDL lifecycle** — Write a single happy-path test that exercises the complete lifecycle in one flow:
   - Create the entity
   - Load it by ID and assert that **every field** matches what was inserted (see Deep Verification below)
   - List and assert the entity appears and that **every field exposed by the list API** matches
   - Update it
   - Load again and assert all fields reflect the update
   - List again and assert all fields reflect the update
   - Delete it
   - Load again and assert it throws `EntityNotFoundException` (or the appropriate not-found exception)
   - List again and assert the entity is no longer present

   Use whatever subset of these steps the entity supports. Any test that is a strict subset of this lifecycle test is redundant and should not be written separately.

2. **Not found** — returns null / throws `NotFoundException` (per service contract)
3. **Constraint violations** — duplicate keys, FK violations throw expected exceptions
4. **Filtering / queries** — verify WHERE clause logic returns correct subsets
5. **Transactions** — rolled-back transactions don't persist changes
6. **Edge cases specific to domain** — e.g. overlapping statement periods, zero-amount transactions

### Deep Verification
Every assertion against a loaded or listed entity — in any test, not just happy path — must verify the full set of fields, not just an ID or a count. Use AssertJ's `.usingRecursiveComparison().ignoringFields(...)` to exclude auto-generated or server-assigned fields (e.g. surrogate IDs, timestamps), then assert those fields separately. For aggregate return types (e.g. a method returning a `Triple` or a wrapper object), verify the contents of every component, not just its size.

---
## Implementation Specifics

### Tech Stack
- **Test framework:** JUnit 5 + Kotlin test DSL
- **DB:** H2 in MySQL compatibility mode (Use the latest stable version of h2 published in maven)
- **Assertions:** AssertJ (preferred) or Kotlin's `kotlin.test` assertions if required

### Test File Structure
```
src/test/kotlin/com/goldberg/law/
  database/
    DatabaseTest.kt          # Abstract base class: sets up H2, runs migrations/schema
    services/
      CheckServiceTest.kt
      ClassificationServiceTest.kt
      ClientServiceTest.kt
      ...
```

### Base Test Class

All DB tests extend `DatabaseTest`, which handles schema setup and teardown.

**Key functions of the base test class:**
- Schema created once per test class (`@BeforeAll`), not per test (performance)
- Data cleared between tests (`@BeforeEach`) for isolation
- Tables listed in reverse dependency order when dropping to avoid FK violations

### Fixtures

To instantiate the various entity objects, use the factory functions in `com.goldberg.law.entity.EntityValues` class.
Prefer to always use the default values unless instantiated in that class as well unless there is good reason for instantiating values per test.
In that case, prefer to create variables in the test function rather than using raw strings/numbers/etc.

If you need to create a new fixture function, you can add one in the EntityValues class if it's needed across multiple tests.  Otherwise, just create it
in the test file where it's relevant.

**Do not extract the service method under test into a helper.** The call to the service method being tested must appear inline in the test body. Wrapping it in a private helper (e.g. `fun insertTestFile(...)` that calls `fileService.insertFile(...)`) hides the core operation and makes the test harder to understand. Helper methods are only appropriate for setting up upstream prerequisites that are not themselves being tested (e.g. inserting a client before testing file operations).

---

## Test Writing Guidance

### Catching incorrect code
**IMPORTANT** Do not necessarily write tests to match the code as written -- the whole point of the unit tests is to catch bugs.  
Write the tests to what the code SHOULD be doing.  When the test fails, we will fix the error  

### Pattern
Each test should follow Arrange / Act / Assert with clear separation.

### Test Class Structure
Group tests by method using JUnit 5 `@Nested` inner classes rather than comment banners like `// --- methodName ---`. Each `@Nested` class should be named after the method it covers (e.g. `inner class InsertClient`, `inner class LoadClient`). This provides IDE navigation, better test-output grouping, and lets you scope `@BeforeEach`/`@AfterEach` setup to just the tests that need it.

### Shared Fixtures and Lifecycle
Upstream prerequisite entities that are needed by every test in a class (or nested class) — e.g. a client and file that must exist before any classification test can run — should be created once in a `@BeforeAll` block, not re-created inside each individual test. If those entities could be mutated during a test, create them in `@BeforeEach` instead. The base class `@BeforeEach` already clears all rows, so any entity inserted in `@BeforeEach` starts fresh every test. Never create throwaway upstream records inline inside individual tests when a shared fixture would serve all tests in the class equally.

### H2 Compatibility Notes

If a service method relies on unsupported MySQL syntax, you can write the test anyway and add a comment.  Don't ignore it, 
as we want the issue to be raised through a test failure.

---

