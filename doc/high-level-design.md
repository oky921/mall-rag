# 商城一期高层设计

## 架构

商城沿用当前单体结构。React 通过 `/api/store/products` 访问 Spring MVC；服务层读取 Spring Data JPA 仓库；JPA 将商品持久化到 MySQL 的 `products` 表。

应用使用惰性 Bean 初始化，使 MySQL 商城与 Milvus RAG 可以独立启动；只有访问模型/RAG 接口时才初始化对应 AI 组件。

## 模块

- `store/Product`：商品持久化模型。
- `store/ProductRepository`：商品查询入口。
- `store/ProductService`：筛选与响应映射。
- `store/StoreController`：只读商城 API。
- `store/StoreDataInitializer`：空库演示数据初始化。
- `frontend/StorePage`：商城浏览、筛选与本地购物车。

## 取舍

一期购物车只保存在浏览器内存，不建立订单表。这样能验证前后端和数据库链路，同时避免在支付、用户和库存规则未明确前固化错误的数据模型。

## 二期架构

二期继续使用现有 Spring MVC、Spring Data JPA、MySQL 和 React，不引入新框架。`StoreSku` 从属于商品；`StoreCartItem` 关联用户与 SKU；`StoreOrder` 聚合不可变的 `StoreOrderItem` 商品快照。

`CurrentStoreUser` 是当前用户边界，现阶段固定返回 `1`，未来可替换为读取 Spring Security 上下文。购物车和订单控制器不接收客户端用户 ID，避免越权式接口契约固化。

下单由单个数据库事务完成。服务按 SKU ID 排序并获取悲观写锁，随后复核购物车归属、SKU 状态和库存，保存订单快照、扣减库存并删除已结算购物车项。任一步失败都会整体回滚。

React 仍由轻量路径分派渲染 `/mall`、商品详情、结算和订单结果；购物车抽屉通过 REST API 读取数据库状态。

## 三期订单状态机

订单状态保持为字符串列并由聚合方法约束迁移：`CREATED -> PAID` 或 `CREATED -> CANCELLED`。终态不允许互相转换。支付和取消都以带悲观写锁的订单查询作为事务入口，因此同一订单的并发操作会串行执行。

取消首次成功时按 SKU ID 排序获取写锁，根据订单明细快照回补库存并减少对应销量；重复取消只返回已取消订单，不再次触碰库存。模拟支付生成唯一支付流水号并记录支付时间，重复支付返回原支付结果。

订单中心通过当前用户边界查询订单，不接受客户端用户 ID。React 增加 `/mall/orders` 列表视图，详情与列表共享支付、取消 API。
