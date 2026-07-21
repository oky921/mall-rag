# Store search index transactional outbox design

## Architecture

`StoreProductCatalogService` is the reusable write boundary. It saves/deletes/unpublishes a product
and appends an outbox row in the same `@Transactional` MySQL transaction. No new public catalog
management endpoint is introduced.

`StoreProductIndexSyncScheduler` asks `StoreProductIndexEventProcessor` to claim a bounded batch.
Claiming runs in a short `REQUIRES_NEW` transaction with a pessimistic row lock. Claimed rows become
PROCESSING and receive a lease deadline in `nextRetryAt`, then the database transaction is released
before embedding and Milvus I/O. Expired PROCESSING leases are eligible for reclaim after a crash.

For every claimed event, the processor queries MySQL by product code:

- active product exists: build the current document and idempotently replace
  `store-product:{productCode}`;
- product is absent or inactive: idempotently delete that stable document ID.

The stored event type is an audit hint, not the mutation decision. This makes an old UPSERT safe after
deletion and an old DELETE safe after recreation.

## Data model and state machine

`store_product_index_outbox` contains `id`, `product_code`, `event_type`, `status`, `retry_count`,
`next_retry_at`, `last_error`, `created_at`, and `processed_at`. Indexes cover claim ordering and
product diagnostics.

State transitions:

`PENDING -> PROCESSING -> COMPLETED`

On failure, `retryCount` is incremented. Before `maxRetries`, the row returns to PENDING with
`nextRetryAt = now + initialBackoff * 2^(retryCount-1)`. At the limit it becomes FAILED. A worker
crash leaves PROCESSING until its lease expires, after which another worker may reclaim it. Milvus
replacement and deletion by stable ID make repeated execution idempotent.

## Index contract

The existing stable ID, source, type, product ID, category, image, link, and price snapshot remain
compatible. `index_version` is a SHA-256 hash of only the fields represented in the document. It is
used by reconciliation and deliberately excludes stock, SKU data, sales, and other high-frequency
facts. Current price is still read from MySQL before a recommendation is returned.

## Reconciliation

The reconciliation service loads active MySQL products and enumerates Milvus documents whose
metadata type is `product`. It upserts missing documents, upserts documents with a different
`index_version`, and deletes IDs that exist only in Milvus. The manual endpoint is
`POST /api/store/search-index/reconcile`. Scheduled reconciliation is disabled by default and gated
by configuration.

Full rebuild remains available and now replaces all active product documents using the same mapping.

## Transaction and consistency boundary

The catalog row and outbox row share one local MySQL transaction. Milvus is intentionally outside
that transaction. A committed event can be retried until Milvus converges, so the design provides
eventual consistency rather than strong consistency across databases.

## Failure recovery

- Temporary dependency failure: exponential retry.
- Process death after claim: lease expiry and reclaim.
- Process death after Milvus success but before completion: idempotent replay.
- Persistent poison event: FAILED with bounded `lastError`; manual repair can be followed by
  reconciliation or explicit event retry tooling.
- Lost/corrupt historical events: reconciliation repairs the derived index from MySQL.

## Test strategy

Focused tests cover transactional rollback, successful UPSERT/DELETE, retry and exhaustion,
idempotent replay, both out-of-order cases, claim exclusion, inventory-free mapping, reconciliation,
and rebuild compatibility. Real MySQL/Milvus/embedding smoke checks are run only when local services
are available.

