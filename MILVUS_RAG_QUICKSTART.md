# Spring AI + Milvus 2.6 Quickstart

This project now includes a minimal Spring AI RAG flow backed by Milvus.

## Required Services

Start Milvus 2.6 and expose the gRPC port on `19530`.

The application defaults to:

```text
MILVUS_HOST=localhost
MILVUS_PORT=19530
MILVUS_DATABASE=default
MILVUS_COLLECTION=first_rag_docs_utf8
MILVUS_EMBEDDING_DIMENSION=1024
```

## Required AI Settings

Set your DashScope/OpenAI-compatible key before starting the backend:

```powershell
$env:DASHSCOPE_API_KEY="your-api-key"
$env:DASHSCOPE_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:DASHSCOPE_MODEL="qwen-plus"
$env:DASHSCOPE_EMBEDDING_MODEL="text-embedding-v4"
$env:DASHSCOPE_EMBEDDING_DIMENSIONS="1024"
```

`DASHSCOPE_EMBEDDING_DIMENSIONS` and `MILVUS_EMBEDDING_DIMENSION` must match the actual embedding vector size returned by your embedding model.

## Start

```powershell
mvn spring-boot:run
```

## Test The RAG Flow

Insert one document:

```powershell
curl -X POST http://localhost:8080/api/rag/documents `
  -H "Content-Type: application/json" `
  -d "{\"content\":\"Milvus is a vector database used for similarity search in RAG applications.\",\"source\":\"manual-test\"}"
```

Search Milvus:

```powershell
curl -X POST http://localhost:8080/api/rag/search `
  -H "Content-Type: application/json" `
  -d "{\"message\":\"What is Milvus used for?\",\"topK\":3}"
```

Ask with RAG:

```powershell
curl -X POST http://localhost:8080/api/rag/chat `
  -H "Content-Type: application/json" `
  -d "{\"message\":\"What is Milvus used for?\",\"topK\":3}"
```
