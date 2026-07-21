# Store and RAG product integration

## Objective

Deliver a product recommendation path that combines Milvus semantic retrieval with authoritative
MySQL product data and links into the existing store detail view.

## Input docs

- `doc/store-rag-integration-proposal.md`
- `doc/store-rag-integration-design.md`

## Expected files

- Product recommendation/index DTOs and services under `src/main/java`
- `MallChatService`, `MallChatResponse`, `StoreController`, repository and RAG document mapping
- Focused service tests under `src/test/java`
- `frontend/src/App.tsx` and `frontend/src/styles.css`

## Steps

- [x] Add the product result contract and hybrid recommendation service.
- [x] Add stable product document replacement and a rebuild endpoint.
- [x] Route product shopping requests through the new service.
- [x] Render live product cards linked to the store detail page.
- [x] Run backend and frontend verification.

## Definition of done

All acceptance checks in the proposal pass, the diff contains no unrelated rewrites, and verification
results are recorded in `doc/tasks/progress.md`.

## Verification notes

- `mvn -q test` passed before the final CSRF matcher adjustment; focused recommendation, index mapping,
  and controller tests passed.
- `npm run build` passed.
- Live MySQL chat check returned `DIG-1002` and `/mall/products/2` for a 1000 yuan budget.
- Live MySQL chat check returned no product for the same headphone request with a 500 yuan budget.
- Live index rebuild reached the endpoint but returned `AI_SERVICE_ERROR`/502 while writing embeddings;
  MySQL fallback remained available.
