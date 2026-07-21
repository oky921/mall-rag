# Store and RAG product integration design

## Data flow

1. `MallChatService` recognizes a product-shopping request.
2. `StoreProductRecommendationService` asks `RagService` for semantic candidates.
3. Candidate `product_id` metadata is interpreted as `StoreProduct.code`.
4. Products are loaded again from MySQL, budget/category constraints are applied, and live response
   fields are produced.
5. If semantic retrieval fails or returns too few live products, deterministic MySQL matching fills
   the remaining slots.
6. React renders `productResults` as product cards linked to `/mall/products/{mysqlId}`.

## Index contract

- Document ID: `store-product:{productCode}`
- `source`: `mysql-store`
- `type`: `product`
- `product_id`: stable `StoreProduct.code`
- `mysql_product_id`: navigation ID, included for diagnostics only
- `category`, `price`, `image_url`: retrieval snapshots, never authoritative at response time
- `link`: `/mall/products/{mysqlId}`

`StoreProductSearchIndexService` replaces documents by stable ID. The manual rebuild endpoint remains
available, and the transactional outbox described in `store-search-index-outbox-design.md` now calls
the same mapping after catalog writes without changing the chat contract.

## Consistency

Milvus is eventually consistent and may contain stale candidates. MySQL hydration is mandatory, so a
missing product is silently removed and current price constraints are always enforced. Stock is not
indexed or returned by chat. The detail and checkout paths continue to perform their existing live
SKU checks.

## Failure behavior

- Milvus unavailable: log and use MySQL fallback matching.
- Product removed from MySQL: discard its vector candidate.
- Index rebuild failure: return the existing RAG service error; MySQL catalog remains unchanged.
- No products satisfy all constraints: return an empty list and a useful chat response.

## Verification

- Unit-test semantic hydration, stale candidate removal, budget filtering, fallback, and index mapping.
- Unit-test product intent routing in `MallChatService`.
- Run `mvn test` and `npm run build`.
