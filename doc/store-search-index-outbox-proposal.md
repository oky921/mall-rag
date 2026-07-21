# Store search index transactional outbox proposal

## Goal

Automatically and reliably converge the Milvus product search index to the current MySQL product
catalog after product creation, modification, deletion, or unpublishing.

## Source of truth

MySQL is the only source of truth. Milvus is a disposable, rebuildable derived index used only for
semantic retrieval. Product recommendations must continue to hydrate candidates from MySQL before
returning current price and availability data.

## Scope

- Persist an UPSERT or DELETE outbox event in the same MySQL transaction as each catalog write.
- Store only the stable `StoreProduct.code` in an event, never a product snapshot.
- Claim due events safely across application instances, retry failures with exponential backoff, and
  move exhausted events to FAILED.
- Resolve every event against current MySQL state at processing time so out-of-order events converge.
- Keep stock, SKU stock, and sales out of Milvus documents.
- Preserve `POST /api/store/search-index/rebuild` and add a manual reconciliation endpoint.
- Make scheduled synchronization and scheduled reconciliation independently configurable.

## Non-goals

- Cross-database atomic commit between MySQL and Milvus.
- A public product administration API or administration UI.
- Replication of inventory or other high-frequency operational fields.
- Exactly-once delivery. Processing is at least once and index mutations are idempotent.

## Success criteria

- A catalog write and its event commit or roll back together.
- Concurrent workers cannot claim the same live lease.
- Temporary Milvus failures retry and exhausted events become FAILED with an error.
- Old event types cannot overwrite current MySQL state.
- Reconciliation repairs missing/stale documents and removes product orphans.
- Backend tests and the frontend production build pass.

## Assumptions

- MySQL 8 supports row-level pessimistic locking.
- The existing Spring AI Milvus collection stores document ID and JSON metadata using its standard
  schema.
- Unpublishing is represented by `StoreProduct.active=false`; existing products default to active.

