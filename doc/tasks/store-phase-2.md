# 商城二期任务

## 目标

完成商品详情、SKU、数据库购物车和模拟订单闭环。

## 输入

- `doc/proposal.md`
- `doc/high-level-design.md`
- `doc/detailed-design.md`

## 预计改动

- `src/main/java/com/example/ragdemo/store/**`
- `src/test/java/com/example/ragdemo/store/**`
- `frontend/src/App.tsx`
- `frontend/src/StorePage.tsx`
- `frontend/src/styles.css`

## 实施步骤

1. 建立 SKU、购物车、订单及订单明细实体和仓库。
2. 实现详情、购物车和订单事务服务/API。
3. 为现有演示商品补齐多规格 SKU 数据。
4. 将前端本地购物车替换为数据库购物车，并增加详情、结算、结果视图。
5. 运行 Maven 测试、TypeScript/Vite 构建和真实 MySQL 联调。

## 完成条件

- 完整链路可通过 UI 和 API 操作。
- 下单库存与购物车变更具备事务一致性。
- 固定用户实现可替换且接口不暴露用户 ID。
- 自动测试和生产构建通过。
