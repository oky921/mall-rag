<<<<<<< HEAD
# spring-ai-rag-demo

这是一个第一阶段的 Spring Boot + Spring AI 后端项目，只完成：

- Spring Boot 项目启动
- REST API
- 阿里云百炼 OpenAI 兼容接口接入
- 最小聊天接口真实调用大模型

本阶段没有 RAG、向量数据库、embedding、文件上传、文档解析、数据库、Redis 或消息队列。

## 版本组合

- Java 21
- Maven
- Spring Boot 4.1.0
- Spring AI 2.0.0

Spring AI 2.0.x 官方支持 Spring Boot 4.0.x 和 4.1.x；OpenAI 兼容模型使用 `spring-ai-starter-model-openai`。

## 项目结构

```text
src/main/java/com/example/ragdemo
  config      # 大模型配置和 ChatClient Bean
  controller  # REST Controller
  dto         # 请求/响应 DTO
  exception   # 全局异常处理
  service     # 大模型调用封装
```

后续扩展 RAG 时，建议新增：

- `service/RagService.java`
- `config/VectorStoreConfig.java`
- `controller/RagController.java`
- `document/` 或 `ingestion/` 包处理文档解析和入库

## 配置

编辑 `src/main/resources/application.yml`：

```yaml
app:
  ai:
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    api-key: todo-replace-with-your-api-key
    model: qwen-plus
```

TODO:

- 将 `api-key` 替换为你的阿里云百炼 DashScope API Key。
- 如果模型名称不同，将 `model` 替换为百炼控制台可用模型。
- 后续生产环境建议改用环境变量：

```powershell
$env:DASHSCOPE_API_KEY="你的 API Key"
$env:DASHSCOPE_MODEL="qwen-plus"
$env:DASHSCOPE_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
```

## 启动

先确认 Maven 使用的是 Java 21：

```bash
mvn -version
```

如果输出不是 Java 21，请先将 `JAVA_HOME` 指向 JDK 21。

本项目已经提供项目级 Maven 配置：

- `.mvn/maven.config`
- `.mvn/settings.xml`
- `.mvn/empty-global-settings.xml`

在项目根目录执行 Maven 命令时，会使用 Maven Central，并屏蔽本机全局 Maven settings 中的公司内网仓库配置。

```bash
mvn spring-boot:run
```

服务默认端口：`8080`

## 测试接口

健康检查：

```bash
curl http://localhost:8080/api/health
```

聊天：

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"你好，请介绍一下你自己\"}"
```

成功响应示例：

```json
{
  "success": true,
  "content": "你好，我是..."
}
```

常见异常：

- `message` 为空：返回 `400 BAD_REQUEST`
- `api-key` 未替换：返回 `500 AI_CONFIGURATION_ERROR`
- 模型调用失败：返回 `502 AI_SERVICE_ERROR`

## 前端联调

前端代码在 `frontend` 目录，适合用 VS Code 单独打开。

```bash
cd frontend
npm install
npm run dev
```

前端默认地址：`http://localhost:5173`

Vite 会把 `/api` 请求代理到后端 `http://localhost:8080`。
=======
# mall-rag
>>>>>>> 17032c55a0d67c2d2ed4411247f76c1ca11a1909
