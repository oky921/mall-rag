# RAG JSON 入库说明

后端现在支持三种入库方式：

- 单条：`POST /api/rag/documents`
- 批量 JSON：`POST /api/rag/documents/batch`
- 上传 JSON 文件：`POST /api/rag/documents/import`

## JSON 格式

单条文档：

```json
{
  "content": "iPhone 15 128GB 蓝色目前有货，活动价 5399 元，支持 7 天无理由退货。",
  "source": "商品库",
  "type": "product",
  "title": "iPhone 15 128GB 蓝色",
  "product_id": "P10001",
  "link": "/products/P10001",
  "metadata": {
    "category": "手机",
    "brand": "Apple",
    "price": 5399,
    "stock": "in_stock"
  }
}
```

批量文件可以直接是数组：

```json
[
  {
    "content": "iPhone 15 128GB 蓝色目前有货，活动价 5399 元。",
    "source": "商品库",
    "type": "product",
    "title": "iPhone 15 128GB 蓝色",
    "product_id": "P10001"
  },
  {
    "content": "7 天无理由退货要求商品外观完好，配件齐全，不影响二次销售。",
    "source": "帮助文档",
    "type": "policy",
    "title": "退货政策"
  }
]
```

也可以是包装对象：

```json
{
  "documents": [
    {
      "content": "满 500 减 50 活动适用于手机、电脑、智能穿戴品类。",
      "source": "活动规则",
      "type": "promotion",
      "title": "618 满减活动"
    }
  ]
}
```

## 从本地文件导入

启动后端并确保 Milvus 可用后，执行：

```powershell
curl.exe -X POST http://localhost:8080/api/rag/documents/import `
  -F "file=@D:\your-path\documents.json"
```

如果文件很小，也可以直接把 JSON body 发给批量接口：

```powershell
Invoke-RestMethod `
  -Uri http://localhost:8080/api/rag/documents/batch `
  -Method Post `
  -ContentType "application/json" `
  -InFile "D:\your-path\documents.json"
```

成功响应示例：

```json
{
  "success": true,
  "documents": 2
}
```

## 建议

价格、库存、订单状态这类强实时数据不建议只依赖向量库。向量库更适合放商品介绍、活动规则、售后政策、FAQ 等文本知识；实时字段建议走业务数据库或 API 查询后再和 RAG 答案组合。
