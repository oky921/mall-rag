# Task: 商城第四阶段用户体系

## Objective

实现用户认证、个人中心、收货地址管理及严格的用户数据隔离。

## Input Docs

- `doc/store-phase-4-proposal.md`
- `doc/store-phase-4-design.md`

## Expected Files

- `pom.xml`
- `src/main/java/com/example/ragdemo/store/**`
- `src/main/resources/application.yml`
- `src/test/java/com/example/ragdemo/store/**`
- `frontend/src/StorePage.tsx`
- `frontend/src/styles.css`

## Implementation Steps

- [x] 增加 Spring Security、用户实体、认证服务和 Session 配置。
- [x] 用认证上下文替换固定用户并保护购物车/订单 API。
- [x] 实现地址实体、服务和 API，并改造下单地址契约。
- [x] 实现登录、注册、个人中心、地址管理和结算选址界面。
- [x] 补充测试、构建和运行验证。

## Tests And Checks

```bash
mvn test
cd frontend && npm run build
```

## Definition Of Done

- [x] 代码实现且未引入明文密码或 API Key。
- [x] 认证和用户隔离测试通过。
- [x] 前后端构建通过。
- [x] 实际服务冒烟验证通过。
- [x] 进度文档已更新。
