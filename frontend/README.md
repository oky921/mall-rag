# spring-ai-rag-demo-frontend

这个目录是 `spring-ai-rag-demo` 的前端联调界面。

## 启动前提

先启动后端：

```bash
mvn spring-boot:run
```

后端地址默认是：

```text
http://localhost:8080
```

## 前端启动

在 VS Code 中打开 `frontend` 目录，然后执行：

```bash
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

Vite 已经配置代理：

```text
/api -> http://localhost:8080
```

所以前端请求 `/api/health` 和 `/api/chat` 时，会自动转发到 Spring Boot。

## 测试顺序

1. 启动 Spring Boot 后端。
2. 启动前端。
3. 打开 `http://localhost:5173`。
4. 页面左侧显示“后端在线”。
5. 在输入框发送消息。

如果聊天接口返回 API Key 未配置，请检查后端 `src/main/resources/application.yml` 中的 `app.ai.api-key`。
