# 商城第四阶段设计

## 架构与边界

Spring Security 负责认证、Session、CSRF 和受保护路由。`SecurityCurrentStoreUser` 从 `SecurityContext` 读取用户 ID，替换固定用户实现。商品读接口公开，`/api/store/auth/**` 提供认证流程，其余购物车、订单、地址和账号接口需要认证。

认证主体使用 `StoreUserPrincipal`，仅包含稳定身份和展示字段。密码校验由 JPA `UserDetailsService` 与 `BCryptPasswordEncoder` 完成。注册成功后创建认证上下文并保存到 HttpSession；登录使用 `AuthenticationManager`，成功后更换 Session ID 以防固定会话攻击。

## API

- `GET /api/store/auth/csrf`：初始化 CSRF Token。
- `POST /api/store/auth/register`：`{username,password,displayName}`，成功后自动登录。
- `POST /api/store/auth/login`：`{username,password}`。
- `POST /api/store/auth/logout`：使 Session 失效。
- `GET /api/store/account`：当前用户资料。
- `PUT /api/store/account`：更新昵称和手机号。
- `GET /api/store/addresses`：当前用户地址列表，默认地址优先。
- `POST /api/store/addresses`：新增地址。
- `PUT /api/store/addresses/{id}`：编辑当前用户地址。
- `DELETE /api/store/addresses/{id}`：删除当前用户地址。
- `PUT /api/store/addresses/{id}/default`：设为默认地址。
- `POST /api/store/orders`：请求改为 `{cartItemIds,addressId}`。

## 地址与订单规则

地址服务的所有查询都包含 `userId`。新增第一条地址自动设为默认；显式设默认或保存 `defaultAddress=true` 时，事务内先清除该用户其他默认标记。删除默认地址后选择按更新时间降序的第一条作为新默认。

下单事务先按当前用户读取地址，再读取当前用户购物车，随后锁定 SKU。订单继续保存收货人、电话和拼接后的完整地址快照，因此后续编辑或删除地址不会改变历史订单。

## 安全与错误

- 认证失败统一返回 JSON 401，不重定向 HTML 登录页。
- 权限不足返回 JSON 403。
- 用户名冲突返回 409；请求校验错误返回 400；越权资源对外返回 404。
- Session Cookie 为 HttpOnly、SameSite=Lax；Secure 由 `APP_SESSION_COOKIE_SECURE` 配置。
- XSRF Token Cookie 可被前端读取，但不包含认证信息。

## 前端

`StorePage` 维护当前用户和 CSRF Token。公开浏览无需登录；打开购物车、加入购物车或进入订单/结算/账号路由时，未登录用户跳转 `/mall/login?next=...`。新增登录注册页、个人中心和地址管理页。结算页读取地址簿并以单选列表选择收货地址。

## 测试策略

- 单元测试：用户注册校验、地址归属/default 规则、下单地址归属。
- Security MockMvc：公开商品接口、未登录 401、登录 Session、CSRF 拒绝。
- 现有购物车和订单测试继续验证服务层用户 ID 过滤。
- 前端 TypeScript 与 Vite 生产构建验证路由和类型契约。

## 风险与后续

- `ddl-auto=update` 不适合正式生产发布，后续应补 Flyway/Liquibase。
- 单机 HttpSession 不支持多实例共享；扩容前应引入 Spring Session Redis。
- 本阶段不提供密码找回，部署前需要补充账号恢复和登录限流。

