# 商城三期：订单中心

## 目标

实现订单列表、状态筛选、模拟支付和取消订单库存回补。

## 预计改动

- `src/main/java/com/example/ragdemo/store/StoreOrder*`
- `src/main/java/com/example/ragdemo/store/StoreSku*`
- `src/test/java/com/example/ragdemo/store/StoreOrderServiceTest.java`
- `frontend/src/StorePage.tsx`
- `frontend/src/styles.css`

## 步骤与检查

1. 扩展订单支付/取消字段与状态方法。
2. 增加锁定订单查询、订单列表和状态操作 API。
3. 实现取消时的 SKU 库存与销量回补。
4. 增加状态机、幂等与库存测试。
5. 实现订单中心和详情操作。
6. 运行 Maven 测试、前端构建、MySQL 联调和响应式视觉检查。

## 完成条件

- 状态迁移合法且并发安全。
- 取消库存只回补一次。
- 订单中心可筛选并执行支付或取消。
- 自动测试、构建和实际接口联调通过。
