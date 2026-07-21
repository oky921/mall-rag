# Store search index outbox progress

## Current status

Implementation and automated verification complete. External Embedding integration was not run
because the execution policy requires separate informed approval before local product text is sent to
a third-party API.

## Tasks

- [x] Transactional catalog writes and outbox persistence
- [x] Idempotent Milvus synchronization worker
- [x] Reconciliation and manual trigger
- [x] Automated verification
- [x] Local MySQL schema and application smoke verification
- [ ] External Embedding and Milvus end-to-end mutation verification (approval required)

## Decisions

- MySQL is authoritative and Milvus is rebuildable derived state.
- Events contain only product code and are resolved against current MySQL state.
- PROCESSING uses `nextRetryAt` as a lease deadline for crash recovery.
- Document version is a hash of indexed fields and excludes inventory and sales.

## Commands run

- Repository, Git status, existing design, product write paths, index service, and tests inspected.
- `mvn test`: 56 tests passed, including the two-transaction concurrent claim test.
- `npm run build`: passed (TypeScript and Vite production build).
- Local Spring Boot smoke run: MySQL 8.0.31 connected, Outbox table/indexes created, all 8 existing
  products active, and `GET /api/store/products` returned HTTP 200 with 8 products.

## Blockers

- External Embedding test requires explicit informed approval because product text leaves the local
  environment. The supplied API key was not written to the repository.
