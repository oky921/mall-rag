# Mall Image RAG

This project supports a separate product-image vector collection powered by DashScope
`qwen3-vl-embedding` and Milvus.

## Environment

Do not put API keys in source files. Set them before starting the backend:

```powershell
$env:DASHSCOPE_API_KEY="your-dashscope-api-key"
$env:DASHSCOPE_MULTIMODAL_EMBEDDING_MODEL="qwen3-vl-embedding"
$env:APP_IMAGE_RAG_MILVUS_COLLECTION="mall_product_images"
```

Milvus defaults reuse the existing Spring AI Milvus host, port, and database:

```text
spring.ai.vectorstore.milvus.client.host=localhost
spring.ai.vectorstore.milvus.client.port=19530
spring.ai.vectorstore.milvus.database-name=default
```

You can also override the image collection connection directly:

```powershell
$env:APP_IMAGE_RAG_MILVUS_URI="http://localhost:19530"
$env:APP_IMAGE_RAG_MILVUS_DATABASE="default"
$env:APP_IMAGE_RAG_MILVUS_COLLECTION="mall_product_images"
```

## Ingest Product Images

```powershell
curl.exe -X POST http://localhost:8080/api/mall/images `
  -H "Content-Type: application/json" `
  -d "{\"image_url\":\"https://example.com/products/P10001.jpg\",\"product_id\":\"P10001\",\"sku_id\":\"P10001-blue\",\"title\":\"iPhone 15 Blue 128GB\",\"link\":\"/products/P10001\",\"metadata\":{\"category\":\"phone\",\"brand\":\"Apple\",\"color\":\"blue\"}}"
```

Batch import:

```powershell
curl.exe -X POST http://localhost:8080/api/mall/images/batch `
  -H "Content-Type: application/json" `
  -d "{\"images\":[{\"image_url\":\"https://example.com/a.jpg\",\"product_id\":\"P1\",\"title\":\"Product A\"},{\"image_url\":\"https://example.com/b.jpg\",\"product_id\":\"P2\",\"title\":\"Product B\"}]}"
```

The service creates the Milvus collection on first use with the vector dimension returned by
`qwen3-vl-embedding`.

## Search

Text-to-image search:

```powershell
curl.exe -X POST http://localhost:8080/api/mall/images/search `
  -H "Content-Type: application/json" `
  -d "{\"query\":\"blue phone product image\",\"topK\":5}"
```

Image URL search:

```powershell
curl.exe -X POST http://localhost:8080/api/mall/images/search `
  -H "Content-Type: application/json" `
  -d "{\"image_url\":\"https://example.com/query.jpg\",\"topK\":5}"
```

Upload image search:

```powershell
curl.exe -X POST http://localhost:8080/api/mall/images/search-by-upload `
  -F "file=@D:\your-path\query.jpg" `
  -F "topK=5"
```

Optional Milvus filter:

```json
{
  "query": "white running shoes",
  "topK": 5,
  "filter": "metadata[\"category\"] == \"shoes\""
}
```

