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

## 功能演示

本项目支持多种交互模式，包括文本搜索、图片搜索和对话推荐。以下是实际运行效果演示：

### 1. 系统启动
```
PS D:\downfile\FirstRag> $env:DASHSCOPE_API_KEY="sk-d1219710ff5248b6a8cb4c54a9c38082"
PS D:\downfile\FirstRag> $env:APP_VECTORSTORE_TYPE="milvus"
PS D:\downfile\FirstRag> mvn spring-boot:run
```

后端成功启动后，访问前端地址即可开始使用。

### 2. 前端交互界面
项目提供了一个现代化的 Web UI，用户可以通过以下方式与系统交互：

**功能模块：**
- 📱 给我找一款手机 - 文本驱动的手机搜索
- 💄 展示化妆品 - 化妆品分类和搜索
- 🧥 找一件羽绒服 - 衣服分类和查询
- 🔗 相似图片搜索 - 上传图片找相似产品

**交互流程示例：**

**场景 1: 文本搜索手机**
- 用户输入："给我找一款手机"
- 系统通过向量检索返回相关手机商品
- 展示多个手机图片选项（商品 ID: P10001, P10002, P10003 等）

**场景 2: 图片搜索衣服**
- 用户输入："给我推荐一下类似的衣服"
- 系统识别图片内容（例如黄色羽绒服）
- 返回相似的衣服产品列表（Down jacket, Clothes image 等）

**场景 3: 自然对话推荐**
- 用户提问："你们还有哪些商品"
- 系统列出商品库中的所有分类
- 返回包括：
  - 📱 数码类产品：手机、电脑、耳机、智能设备
  - 👔 服饰鞋包：男女衣、鞋、箱包、配件
  - 💄 美妆个护：护肤品、彩妆、香水、洗护用品
  - 🏠 家居生活：家具、家纺、日用百货、厨具
  - 🍔 食品母婴：食品、母婴用品等

## 核心功能

### 1. 多模式搜索能力

**文本搜索**
- 用户输入自然语言查询
- 系统通过向量化检索找到相关商品
- 支持复杂的商品描述理解

**图片搜索**
- 支持上传图片进行相似度搜索
- 使用 DashScope 多模态 Embedding (qwen3-vl-embedding)
- 快速找到相似的商品图片

**对话推荐**
- 支持多轮对话，系统记忆上文
- 基于用户需求动态生成推荐
- 支持产品分类导航

### 2. 向量检索与重排

- **向量存储**：使用 Milvus 存储文档和图片向量
- **相似度计算**：支持 COSINE 距离度量
- **重排优化**：基于 RerankModel 进行二次排序，提升相关性
- **动态过滤**：支持 Milvus 过滤表达式（如按分类、价格等）

### 3. 模型路由与容错

项目支持多个 AI 模型的并行配置和自动降级：

```
聊天模型候选：
├── chat-primary: qwen3.7-max (优先级 100)
└── chat-qwen35-plus: qwen3.5-plus (优先级 200)

Embedding 模型候选：
├── embedding-primary: text-embedding-v4 (优先级 100)
└── embedding-text-v3: text-embedding-v3 (优先级 200)
```

当主模型失败次数超过阈值时，系统自动切换到备选模型。

### 4. 分布式限流

基于 Redis 的队列式限流：
- 支持并发数控制
- 支持请求节流
- 可为不同业务模块配置不同限流策略
- 示例配置：
  ```
  chat: max-concurrent: 2
  rag: max-concurrent: 1
  ```

## 使用场景

这个项目适用于以下场景：

1. **电商推荐系统** - 基于用户描述和图片快速推荐商品
2. **商品搜索引擎** - 支持多模态搜索和智能问答
3. **客服助手** - 为用户快速查找相关商品和回答常见问题
4. **库存管理** - 支持文本和图片的库存检索

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

### 图片 RAG 检索

```bash
curl -X POST http://localhost:8080/api/mall/images/search \
  -H "Content-Type: application/json" \
  -d '{"query":"blue phone product image","topK":5}'
```

### 图片上传搜索

```bash
curl -X POST http://localhost:8080/api/mall/images/search-by-upload \
  -F "file=@query.jpg" \
  -F "topK=5"
```

### 商城聊天推荐

```bash
curl -X POST http://localhost:8080/api/mall/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"给我找一款蓝色的手机","topK":3}'
```

### 带图片的智能推荐

```bash
curl -X POST http://localhost:8080/api/mall/chat/with-image \
  -F "message=给我推荐类似的衣服" \
  -F "file=@product.jpg" \
  -F "topK=5"
```

## 部署与扩展

### Docker 容器化

如果你想将项目容器化，可以为后端和前端分别创建 Dockerfile：

```dockerfile
# 后端 Dockerfile
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/spring-ai-rag-demo-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 生产环境建议

1. **API 安全**
   - 使用 API Key 认证
   - 限制请求频率和大小

2. **数据治理**
   - 定期清理过期向量
   - 优化 Milvus 索引
   - 备份知识库数据

3. **监控告警**
   - 监控模型调用延迟
   - 监控向量检索效率
   - 记录系统错误日志

4. **性能优化**
   - 使用 CDN 加速图片访问
   - 优化向量检索参数（topK、距离阈值）
   - 启用 Redis 缓存热点数据

## 常见问题

**Q: 如何更换大模型供应商？**
A: 编辑 `application.yml` 中的 `app.ai` 配置，更改 `base-url` 和 `api-key`。

**Q: 向量维度不匹配如何处理？**
A: 确保 `DASHSCOPE_EMBEDDING_DIMENSIONS` 与 `MILVUS_EMBEDDING_DIMENSION` 一致，并与实际 embedding 模型的输出维度相符。

**Q: 如何调试限流问题？**
A: 检查 Redis 连接和配置中的 `app.ratelimit` 参数，查看 `RateLimitController` 的响应。

**Q: 可以离线使用吗？**
A: 不可以。系统需要：
- 网络连接到 DashScope / OpenAI API
- 连接到 Milvus 向量数据库
- 连接到 Redis 实例

## 文件组织建议

如果你想在项目中保存演示截图，建议按以下结构组织：

```
docs/
  screenshots/
    01-backend-startup.png       # 后端启动界面
    02-frontend-home.png         # 前端首页
    03-phone-search.png          # 手机搜索结果
    04-clothes-search.png        # 衣服搜索结果
    05-similar-image-search.png  # 相似图片搜索
    06-chat-recommendation.png   # 聊天推荐效果
  README-Screenshots.md          # 截图说明文档
```

## 文档说明

完整的项目文档包括：

- [docs/DEMO_WALKTHROUGH.md](docs/DEMO_WALKTHROUGH.md) - **详细演示指南**（包含所有截图说明和使用场景）
- [MILVUS_RAG_QUICKSTART.md](MILVUS_RAG_QUICKSTART.md) - Milvus 向量数据库快速开始
- [docs/mall-image-rag.md](docs/mall-image-rag.md) - 商品图片 RAG 实现细节
- [docs/rag-json-ingest.md](docs/rag-json-ingest.md) - JSON 文档批量导入说明

## 备注

如果你想把这个项目继续扩展为完整的生产级 RAG 服务，可以进一步补充：

- 文档解析与批量导入
- 权限与用户认证
- 日志与监控
- 向量索引优化与数据治理
