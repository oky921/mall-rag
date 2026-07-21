# Store and RAG product integration proposal

## Goal

Connect conversational product discovery to the existing MySQL-backed store. A user can describe a
budget, category, use case, or feature and receive live product cards that open the existing product
detail page.

## Scope

- Treat MySQL as the source of truth for product identity, price, image, and availability.
- Use Milvus only to retrieve semantically similar product descriptions.
- Hydrate every Milvus candidate from MySQL before returning it.
- Omit stock counts from chat results; the existing detail page remains responsible for live SKU stock.
- Provide a repeatable endpoint that rebuilds the product documents in Milvus from MySQL.
- Keep the existing image-search and general knowledge-base behavior compatible.
- Fall back to MySQL-side matching when Milvus is unavailable or has not been indexed yet.

## Non-goals

- Distributed transactions between MySQL and Milvus.
- Real-time stock replication into Milvus.
- A product administration UI. Automatic synchronization is covered by the follow-on transactional
  outbox design in `store-search-index-outbox-design.md`.
- Changing checkout or inventory deduction behavior.

## Acceptance checks

- A product-oriented chat request returns `productResults` populated from MySQL.
- Every result has a valid `/mall/products/{id}` URL and contains no stock field.
- Explicit budgets are enforced against the current MySQL price.
- Missing or stale Milvus product IDs are discarded.
- Rebuilding the search index creates stable product document IDs.
- Backend tests and the frontend production build pass.
