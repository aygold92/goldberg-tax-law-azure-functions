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

1. **Happy path CRUDL** — insert, find by id, find by filter, list, update, delete
2. **Not found** — returns null / throws `NotFoundException` (per service contract)
3. **Constraint violations** — duplicate keys, FK violations throw expected exceptions
4. **Filtering / queries** — verify WHERE clause logic returns correct subsets
5. **Transactions** — rolled-back transactions don't persist changes
6. **Edge cases specific to domain** — e.g. overlapping statement periods, zero-amount transactions

### 

### Test Structure
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

---
## Implementation Specifics

### Tech Stack
- **Test framework:** JUnit 5 + Kotlin test DSL
- **DB:** H2 in MySQL compatibility mode (Use the latest stable version of h2 published in maven)
- **Assertions:** AssertJ (preferred) or Kotlin's `kotlin.test` assertions if required

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

---

## Test Writing Guidance

### Catching incorrect code
**IMPORTANT** Do not necessarily write tests to match the code as written -- the whole point of the unit tests is to catch bugs.  
Write the tests to what the code SHOULD be doing.  When the test fails, we will fix the error  

### Pattern
Each test should follow Arrange / Act / Assert with clear separation.

### H2 Compatibility Notes

If a service method relies on unsupported MySQL syntax, you can write the test anyway and add a comment.  Don't ignore it, 
as we want the issue to be raised through a test failure.

---

