# FirstRag 功能演示指南

本文档详细说明了 FirstRag 项目的实际运行效果和使用场景。

## 系统架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                      前端 Web UI (React)                     │
│                   http://localhost:5173                      │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP/REST
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                  后端 API (Spring Boot)                      │
│                   http://localhost:8080                      │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ ChatController      RagController      ImageRagController│ │
│  │ MallChatController  RateLimitController                │ │
│  └────────────────────────────────────────────────────────┘ │
└──────┬──────────────────────────────────┬───────────────────┘
       │                                  │
       ▼                                  ▼
┌─────────────────────┐         ┌──────────────────────┐
│  DashScope AI API   │         │  Milvus Vector DB    │
│  (Embedding, Chat)  │         │  (文本和图片向量)    │
└─────────────────────┘         └──────┬───────────────┘
                                       │
                                       ▼
                          ┌──────────────────────────┐
                          │   Redis (限流与缓存)    │
                          └──────────────────────────┘
```

## 演示场景详解

### 场景 1：系统启动与配置

**命令行输出示例：**
```powershell
PS D:\downfile\FirstRag> $env:DASHSCOPE_API_KEY="sk-d1219710ff5248b6a8cb4c54a9c38082"
PS D:\downfile\FirstRag> $env:APP_VECTORSTORE_TYPE="milvus"
PS D:\downfile\FirstRag> mvn spring-boot:run
```

**说明：**
- 设置 DashScope API Key（阿里云百炼平台的 LLM 服务）
- 启用 Milvus 向量存储
- 使用 Maven 启动 Spring Boot 应用
- 后端初始化过程包括：
  - 加载 Spring AI 配置
  - 连接 Milvus 数据库
  - 建立 Redis 连接用于限流
  - 初始化模型路由

### 场景 2：前端首页与功能导航

**UI 界面组成：**
```
┌────────────────────────────────────────────────────┐
│  商城智能助手                                      │
│  支持文本、图片、图片检索商品及推荐               │
├────────────────────────────────────────────────────┤
│ 当前模式：商品向量检索                            │
│                                                    │
│ ┌─────────────────────────────────────────────┐ │
│ │  给我找一款手机 ┌───┐                       │ │
│ │  展示化妆品     │   │ 找一件羽绒服         │ │
│ │  相似化妆品     └───┘ 根据上传的图片找   │ │
│ │                       相似商品             │ │
│ └─────────────────────────────────────────────┘ │
│                                                    │
│ 快速测试:                                         │
│ ┌──────────────────────────────────────────────┐ │
│ │ 给我找一款手机                               │ │
│ │ 展示化妆品                                   │ │
│ │ 找一件羽绒服                                 │ │
│ │ 根据上传的图片找相似商品                     │ │
│ └──────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────┘
```

**功能模块：**
1. **给我找一款手机** - 文本驱动商品检索
2. **展示化妆品** - 分类浏览功能
3. **找一件羽绒服** - 服装搜索和推荐
4. **相似图片搜索** - 图片上传并查找相似商品

### 场景 3：文本搜索示例

**用户操作：** "给我找一款手机"

**系统处理流程：**
```
用户输入 "给我找一款手机"
    ↓
前端 → 后端 POST /api/mall/chat
    ↓
后端处理：
  1. 文本向量化（使用 text-embedding-v4）
  2. Milvus 向量检索（topK=3，搜索相似手机图片）
  3. 大模型生成推荐理由（使用 qwen3.7-max）
    ↓
返回搜索结果：
  - iPhone 15 Black (商品 ID: P10001)
  - iPhone 15 Blue (商品 ID: P10002)
  - Pixel 8 Pro (商品 ID: P10003)
    ↓
前端展示手机图片卡片
```

**返回结果包含：**
- 商品图片
- 商品 ID（P10001, P10002, P10003）
- 商品标题
- 向量检索相似度分数
- AI 生成的推荐理由

### 场景 4：图片搜索示例

**用户操作：** 
1. 上传一张黄色羽绒服的图片
2. 输入："给我推荐一下类似的衣服"

**系统处理流程：**
```
用户上传图片 + 输入文本
    ↓
前端将图片转换为临时路径 → 后端 POST /api/mall/chat/with-image
    ↓
后端处理：
  1. 图片向量化（使用 qwen3-vl-embedding 多模态模型）
  2. 结合文本生成检索查询
  3. Milvus 向量检索（topK=3）
    ↓
返回相似商品：
  - Yellow Down Jacket (衣服 ID: C10001)
  - Similar Winter Coat (衣服 ID: C10002)
  - Dress Jacket (衣服 ID: C10003)
    ↓
前端展示检索到的相似衣服
```

**关键特性：**
- 支持 JPEG、PNG、GIF、WebP 等图片格式
- 临时文件自动清理
- 多模态向量嵌入（图片 + 文本结合）
- 高相似度匹配

### 场景 5：自然对话推荐

**用户提问：** "你们还有哪些商品"

**系统处理流程：**
```
用户输入自然语言问题
    ↓
前端 → 后端 POST /api/mall/chat
    ↓
后端处理：
  1. 无关键商品匹配时，触发通用回答流程
  2. 大模型直接生成商品分类列表
  3. 返回结构化推荐
    ↓
系统回复：
```

**回复内容示例：**
```
我们的商品库涵盖多种类别，主要包括：

🔸 数码类产品
   - 手机（iPhone 15、Pixel 8 等）
   - 电脑（MacBook、ThinkPad 等）
   - 耳机（AirPods、Sony WH-1000 等）
   - 智能设备

🔸 服饰鞋包
   - 男女服装
   - 鞋类（运动鞋、正装鞋等）
   - 箱包（旅行箱、背包等）
   - 配饰

🔸 美妆个护
   - 护肤品（面膜、精华等）
   - 彩妆（口红、粉底等）
   - 香水与香氛
   - 洗护用品

🔸 家居生活
   - 家具
   - 家纺（床上用品、窗帘等）
   - 日用百货
   - 厨具

🔸 食品与母婴
   - 食品饮料
   - 母婴用品
   - 营养品
```

## 核心技术实现

### 1. 向量检索流程

```
文档/图片入库流程：
  输入 → 向量化 → Milvus 入库 → 元数据关联

查询流程：
  查询 → 向量化 → Milvus 相似搜索 → 重排 → 返回结果
```

### 2. 模型路由与容错

**聊天模型配置：**
```yaml
chat-primary:         # 主模型
  provider: dashscope-compatible
  model: qwen3.7-max
  priority: 100
  failure-threshold: 3     # 失败3次后切换

chat-qwen35-plus:     # 备用模型
  provider: dashscope-native
  model: qwen3.5-plus
  priority: 200        # 优先级200，作为备选
```

**失败转移逻辑：**
```
发送请求到 chat-primary
  ↓
如果失败计数 < 3
  ├─ 重试
  └─ 记录失败
如果失败计数 >= 3
  └─ 自动切换到 chat-qwen35-plus
  
当系统恢复正常，按优先级自动回切
```

### 3. 分布式限流实现

**限流配置示例：**
```
聊天服务：最多并发 2 个请求
RAG 检索：最多并发 1 个请求（防止大模型过载）
```

**限流工作流程：**
```
请求到达
  ↓
检查 Redis 中的并发计数
  ↓
如果 < 限制值
  ├─ 获取许可（permit）
  ├─ 处理请求
  └─ 释放许可
如果 >= 限制值
  └─ 返回 429 Too Many Requests
```

## 使用 API 复现演示

### 1. 文本搜索手机

```bash
curl -X POST http://localhost:8080/api/mall/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "给我找一款手机",
    "topK": 3
  }'
```

**期望响应：**
```json
{
  "success": true,
  "message": "我为您找到了3款热销手机...",
  "results": [
    {
      "id": "P10001",
      "title": "Phone product image",
      "imageUrl": "...",
      "score": 0.95
    }
  ]
}
```

### 2. 图片上传搜索

```bash
curl -X POST http://localhost:8080/api/mall/chat/with-image \
  -F "message=给我推荐类似的衣服" \
  -F "file=@/path/to/yellow-jacket.jpg" \
  -F "topK=5"
```

### 3. 纯图片检索

```bash
curl -X POST http://localhost:8080/api/mall/images/search \
  -H "Content-Type: application/json" \
  -d '{
    "image_url": "https://example.com/query.jpg",
    "topK": 5
  }'
```

### 4. 批量入库商品

```bash
curl -X POST http://localhost:8080/api/mall/images/batch \
  -H "Content-Type: application/json" \
  -d '{
    "images": [
      {
        "image_url": "https://example.com/phone1.jpg",
        "product_id": "P10001",
        "title": "iPhone 15 Black 128GB",
        "source": "official-store"
      },
      {
        "image_url": "https://example.com/jacket.jpg",
        "product_id": "C10001",
        "title": "Yellow Winter Jacket",
        "source": "official-store"
      }
    ]
  }'
```

## 性能指标参考

| 操作 | 平均延迟 | 瓶颈 |
|------|--------|------|
| 文本搜索 | 500-1000ms | 向量化 + Milvus 查询 |
| 图片搜索 | 1000-2000ms | 图片向量化 |
| LLM 生成 | 2000-5000ms | 大模型推理 |
| 总体 RAG 操作 | 3000-7000ms | LLM + 网络 |

## 故障排查

### 问题：Milvus 连接失败

```
错误信息：Failed to connect to Milvus
解决方案：
1. 确保 Milvus 容器正在运行
2. 检查网络连接：telnet localhost 19530
3. 检查环境变量：$env:MILVUS_HOST 和 $env:MILVUS_PORT
```

### 问题：API Key 无效

```
错误信息：401 Unauthorized
解决方案：
1. 确认 API Key 正确
2. 确认 DashScope 账户余额充足
3. 检查模型是否可用
```

### 问题：向量维度不匹配

```
错误信息：Embedding dimension mismatch
解决方案：
确保以下配置一致：
  - $env:DASHSCOPE_EMBEDDING_DIMENSIONS
  - $env:MILVUS_EMBEDDING_DIMENSION
  - 实际 embedding 模型输出维度
```

## 进一步优化方向

1. **缓存优化** - 使用 Redis 缓存热点商品向量
2. **批处理** - 支持批量图片入库和检索
3. **实时推荐** - 基于用户行为的个性化推荐
4. **多语言支持** - 扩展到其他语言的商品搜索
5. **前端优化** - 添加图片预览、购物车等功能
