# FirstRag

FirstRag 是一个基于 Spring Boot 4、Spring AI 2 和 Milvus 的 RAG（检索增强生成）示例项目，旨在演示如何将大模型、向量数据库和业务数据结合起来，构建可扩展的智能问答与商品搜索体验。

## 项目简介

这个项目包含以下核心能力：

- 基于 Spring AI 的聊天接口，支持接入 DashScope / OpenAI 兼容模型
- 文本 RAG：将文档向量化后写入 Milvus，并通过检索生成回答
- 图片 RAG：支持商品图片向量检索与相似图搜索
- 模型路由与降级：支持多模型候选路由，提升可用性与容错能力
- 分布式限流：基于 Redis 的队列式限流能力
- 前端交互界面：使用 React + Vite 提供可视化访问入口

## 主要技术栈

- Java 21
- Maven
- Spring Boot 4.1.0
- Spring AI 2.0.0
- Milvus 2.6
- Redis
- React + Vite + TypeScript
- DashScope SDK

## 项目结构

```text
src/main/java/com/example/ragdemo
  config/           # 配置类、模型路由与 AI 配置
  controller/       # REST Controller，暴露聊天、RAG、图片检索接口
  service/          # 业务服务层，封装大模型调用与向量检索逻辑
  routing/          # 模型路由与失败降级逻辑
  ratelimit/        # 分布式限流实现
  dto/              # 请求与响应 DTO
  exception/        # 全局异常处理
frontend/          # React + Vite 前端项目
docs/              # 项目说明文档
```

## 环境依赖

在启动项目前，请确保以下依赖可用：

- JDK 21
- Maven 3.8+（推荐 3.9+）
- Redis（用于限流）
- Milvus 2.6（用于向量检索）
- DashScope / OpenAI 兼容模型 API Key

## 配置说明

项目配置文件位于 [src/main/resources/application.yml](src/main/resources/application.yml)。

建议在启动前设置以下环境变量（PowerShell 示例）：

```powershell
$env:DASHSCOPE_API_KEY="your-api-key"
$env:DASHSCOPE_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:DASHSCOPE_MODEL="qwen3.7-max"
$env:DASHSCOPE_EMBEDDING_MODEL="text-embedding-v4"
$env:DASHSCOPE_EMBEDDING_DIMENSIONS="1024"
$env:MILVUS_HOST="localhost"
$env:MILVUS_PORT="19530"
$env:APP_REDIS_URL="redis://localhost:6379"
```

## 启动方式

### 1. 启动后端

在项目根目录执行：

```powershell
mvn spring-boot:run
```

服务默认运行在：

- http://localhost:8080

### 2. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

前端默认地址：

- http://localhost:5173

## 主要接口示例

### 健康检查

```bash
curl http://localhost:8080/api/health
```

### 基础聊天

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"你好，请介绍一下你自己"}'
```

### 文本 RAG 入库

```bash
curl -X POST http://localhost:8080/api/rag/documents \
  -H "Content-Type: application/json" \
  -d '{"content":"Milvus 是一个向量数据库，适合做 RAG 检索。","source":"manual-test"}'
```

### 文本 RAG 检索

```bash
curl -X POST http://localhost:8080/api/rag/search \
  -H "Content-Type: application/json" \
  -d '{"message":"Milvus 的用途是什么？","topK":3}'
```

### 图片 RAG 检索

```bash
curl -X POST http://localhost:8080/api/mall/images/search \
  -H "Content-Type: application/json" \
  -d '{"query":"blue phone product image","topK":5}'
```

## 主要技术内容

1. Spring AI 集成
   - 使用 ChatClient 进行大模型调用
   - 支持向量检索与上下文增强生成

2. Milvus 向量数据库
   - 存储文档和图片向量
   - 支持相似度搜索与召回

3. 模型路由与容错
   - 可配置多个模型候选项
   - 支持失败阈值与开放时长控制

4. Redis 分布式限流
   - 基于队列和 Redis 的并发控制与请求节流

5. 前端可视化支持
   - 基于 React + Vite 提供交互式体验

## 文档说明

更多使用说明可以查看：

- [MILVUS_RAG_QUICKSTART.md](MILVUS_RAG_QUICKSTART.md)
- [docs/mall-image-rag.md](docs/mall-image-rag.md)
- [docs/rag-json-ingest.md](docs/rag-json-ingest.md)

## 备注

如果你想把这个项目继续扩展为完整的生产级 RAG 服务，可以进一步补充：

- 文档解析与批量导入
- 权限与用户认证
- 日志与监控
- 向量索引优化与数据治理
