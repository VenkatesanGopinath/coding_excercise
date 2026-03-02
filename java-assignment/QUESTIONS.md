# Questions

Here we have 3 questions related to the code base for you to answer. It is not about right or wrong, but more about what's the reasoning behind your decisions.

1. In this code base, we have some different implementation strategies when it comes to database access layer and manipulation. If you would maintain this code base, would you refactor any of those? Why?

**Answer:**
```txt
Yes. The codebase currently mixes two distinct patterns:

- Products and Stores use Panache's Active Record pattern (entity extends PanacheEntity and
  carries its own persistence methods like `store.persist()`).
- Warehouses use the Repository pattern (WarehouseRepository implements PanacheRepository<DbWarehouse>),
  with a clean separation between the JPA entity (DbWarehouse) and the domain model (Warehouse).

The Warehouse approach is strictly better for a codebase meant to grow and be maintained:

1. Testability: Use cases that depend on a WarehouseStore interface can be unit tested with
   in-memory stubs (as demonstrated in the use case tests) without starting a database.
   Active Record entities are tightly coupled to Hibernate, making pure unit tests impossible.

2. Architecture integrity: The Repository pattern respects the hexagonal architecture already
   established for warehouses, keeping domain logic isolated from persistence concerns.

3. Consistency: A mixed codebase is harder to reason about. New developers need to understand
   two distinct patterns, leading to mistakes and inconsistent code.

If I were maintaining this codebase, I would migrate Product and Store to the Repository pattern
over time — starting with Store (since it already has a dependency on an external gateway) and
Product next. This migration can be done incrementally without breaking existing behaviour.

The one tradeoff is verbosity: the Repository pattern requires more boilerplate classes than
Active Record. For very simple CRUD-only entities with no domain logic, Active Record is fine
in a throw-away prototype. But for anything expected to evolve, the Repository pattern wins.
```
----
2. When it comes to API spec and endpoints handlers, we have an Open API yaml file for the `Warehouse` API from which we generate code, but for the other endpoints - `Product` and `Store` - we just coded directly everything. What would be your thoughts about what are the pros and cons of each approach and what would be your choice?

**Answer:**
```txt
Code-first (Product, Store):
  Pros:
  - Fast to start: no spec file to maintain, just write the class.
  - Less tooling friction: no code-generation step, no generated sources to manage.
  - Easy to iterate during early development when the contract is still evolving.

  Cons:
  - The API contract is implicit — consumers must read source code or rely on auto-generated
    (and potentially incomplete) docs.
  - Nothing enforces consistency between what the server exposes and what clients expect.
  - Harder to coordinate with other teams or generate client SDKs.

API-first / OpenAPI-first (Warehouse):
  Pros:
  - The YAML file is the single source of truth: all parties (backend, frontend, QA) align
    on the same contract before a line of code is written.
  - Auto-generated interfaces enforce the contract at compile time — if the spec changes,
    the implementation must adapt or it won't compile.
  - Enables parallel development: frontend teams can mock the API from the spec while backend
    implements it.
  - Client SDKs can be generated from the same spec, reducing integration bugs.

  Cons:
  - More upfront setup (generator plugin, managing generated sources, IDE config).
  - Requires discipline to keep the YAML in sync when rapid changes are needed.
  - Minor versioning friction: regenerating the spec can override custom annotations or
    force adapter code (e.g. the toWarehouseResponse mapping in this codebase).

My choice: API-first for any API exposed beyond a single team or service.
The contract clarity and compile-time enforcement far outweigh the setup cost. For internal
prototyping or a single-team microservice with no external consumers, code-first is acceptable
in the short term — but the team should plan to formalise the contract before the API stabilises.
In this codebase I would migrate Product and Store to OpenAPI-first to align them with the
Warehouse pattern and make the full API surface visible and enforceable.
```
----
3. Given the need to balance thorough testing with time and resource constraints, how would you prioritize and implement tests for this project? Which types of tests would you focus on, and how would you ensure test coverage remains effective over time?

**Answer:**
```txt
Priority order (highest ROI to lowest):

1. Domain use-case unit tests (highest priority)
   These are fast, require no infrastructure, and directly verify business rules — the part
   of the code most likely to break when requirements change. The hexagonal architecture makes
   this easy: inject in-memory stubs and call the use case directly. Every validation rule
   (BUC uniqueness, location validity, capacity limits, stock matching) must have its own
   test, both for the happy path and for each failure mode. These tests run in milliseconds
   and should be run on every commit.

2. Repository / persistence layer tests (@QuarkusTest with test dev services)
   Quarkus automatically spins up a real PostgreSQL container via Dev Services for tests
   annotated with @QuarkusTest. These tests verify that JPQL queries, entity mappings, and
   transaction boundaries work correctly against a real database without needing manual setup.
   Cover: getAll() returns only active warehouses, findByBusinessUnitCode returns null for
   archived ones, create/update/archive round-trips.

3. API integration tests (@QuarkusTest / @QuarkusIntegrationTest)
   These exercise the full HTTP stack: serialisation, path parameter binding, HTTP status
   codes, and error responses. They are slower but catch wiring bugs that unit tests miss
   (e.g. a missing @Transactional, a wrong HTTP verb binding). Cover the main happy path and
   key error cases (404 on missing resource, 400 on invalid input).

4. Contract tests (future)
   If the Warehouse API is consumed by external clients, add consumer-driven contract tests
   (e.g. Pact) to guard against breaking changes in the OpenAPI spec.

To keep coverage effective over time:
- Treat failing tests as a build blocker (no merging with red CI).
- Require new business rules to be accompanied by a use-case test.
- Review test coverage as part of PRs — focus on branch coverage in use cases, not line
  coverage across the whole project.
- Avoid testing framework internals (e.g. do not write tests for auto-generated Panache
  methods); trust the library and test your code.
```
