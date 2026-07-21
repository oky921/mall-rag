# Task: store search index transactional outbox

## Objective

Implement reliable, idempotent, eventually consistent synchronization from the MySQL product catalog
to the Milvus product search index.

## Input docs

- `doc/store-search-index-outbox-proposal.md`
- `doc/store-search-index-outbox-design.md`
- `doc/store-rag-integration-design.md`

## Expected files

- Outbox/config/catalog-write code under `src/main/java/com/example/ragdemo/store`
- Sync/reconciliation/index adapter code under `src/main/java/com/example/ragdemo/service`
- Configuration in `src/main/resources/application.yml`
- Focused tests under `src/test/java`

## Implementation steps

- [x] Add active product state, outbox entity/repository, and transactional catalog write service.
- [x] Add idempotent index operations and versioned product document mapping.
- [x] Add lease-based claiming, event processing, retry/backoff, and scheduling.
- [x] Add manual and configurable scheduled reconciliation.
- [x] Add required tests and preserve rebuild compatibility.
- [x] Run backend, frontend, and available live integration checks.

## Definition of done

- [x] Implementation and tests match the detailed design.
- [x] `mvn test` passes.
- [x] `npm run build` passes.
- [x] No secrets or unrelated changes are introduced.
