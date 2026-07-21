# 商城一期详细设计

## 数据模型

`products` 包含业务编码、名称、副标题、描述、分类、价格、原价、库存、销量、评分、图片地址、推荐标记和创建时间。业务编码唯一，金额使用 `BigDecimal`。

## API

`GET /api/store/products?category=&keyword=&featured=` 返回 `{ items, total }`。所有条件可选；关键词匹配商品名、副标题和描述，结果按推荐状态、销量和 ID 排序。

`GET /api/store/categories` 返回当前商品分类及数量。

## 前端

根据 `window.location.pathname` 渲染智能助手、商城或原有占位页。商城加载接口数据，搜索和分类改变时重新请求。购物车在前端记录商品及数量，右侧抽屉展示小计。

## 错误处理

商品请求失败时显示错误状态与重试按钮；图片失败时隐藏损坏图片并显示商品类别占位。数据库配置支持环境变量覆盖。

## 验证

- Repository/MockMvc 集成测试验证筛选接口与种子数据。
- `mvn test` 验证后端。
- `npm run build` 验证 TypeScript 和前端产物。
- 启动服务后使用 HTTP 请求检查真实 MySQL 返回。

## 二期数据模型

- `store_skus`：商品、SKU 编码、JSON 规格、售价、原价、库存、销量、图片、启用状态和时间戳。
- `store_cart_items`：用户、SKU、数量和时间戳；`user_id + sku_id` 唯一。
- `store_orders`：订单号、用户、状态、总金额、收货信息和时间戳。
- `store_order_items`：订单、商品/SKU 标识与名称快照、规格快照、价格、数量和小计。

金额统一使用 `BigDecimal`。规格通过 Jackson 在 API 边界转换为对象，在数据库中保存 JSON 字符串。

## 二期 API

- `GET /api/store/products/{id}`：商品详情及启用 SKU。
- `GET /api/store/cart`：当前演示用户购物车及合计。
- `POST /api/store/cart`：请求 `{skuId, quantity}`，同 SKU 已存在时累加。
- `PUT /api/store/cart/{itemId}`：请求 `{quantity}`，设置绝对数量。
- `DELETE /api/store/cart/{itemId}`：删除当前用户购物车项。
- `POST /api/store/orders`：请求收货信息及 `cartItemIds`，创建模拟订单。
- `GET /api/store/orders/{id}`：查看当前用户订单结果。

业务校验错误使用明确的 HTTP 400/404/409 状态；控制器不允许传入用户 ID。

## 二期边界情况

- 禁止选择未启用、库存为零或不属于当前用户的数据。
- 购物车数量范围为 `1..SKU库存`。
- 下单项去重并要求非空；总价以事务内 SKU 当前价格计算。
- SKU 锁按 ID 排序获取，降低多商品订单死锁概率。
- 初始化器即使商品表已有一期数据，也会为缺少 SKU 的演示商品补齐规格。

## 三期 API 与事务

- `GET /api/store/orders?status=`：返回当前用户订单，状态可选 `CREATED`、`PAID`、`CANCELLED`。
- `POST /api/store/orders/{id}/pay`：模拟支付；待支付转已支付，重复支付幂等。
- `POST /api/store/orders/{id}/cancel`：取消待支付订单；首次取消回补库存，重复取消幂等。

订单新增 `payment_no`、`paid_at`、`cancelled_at` 字段。支付流水以时间和随机段生成，数据库唯一约束兜底。订单响应增加支付与取消时间。

取消事务先锁订单，再按 SKU ID 排序锁 SKU。任一 SKU 丢失会使整个取消事务回滚并返回冲突，避免部分回补。已支付订单取消、已取消订单支付返回 HTTP 409。
