# Tasks
- [x] Task 1: 建立 `iam_session` 领域模型与持久化链路
  - [x] SubTask 1.1: 新增 `IamSession`、`IamSessionStatus` 与 `IamSessionRepository`，明确 `ACTIVE/REVOKED` 与 `isActive/revoke/extendTo` 行为
  - [x] SubTask 1.2: 新增 `IamSessionDO`、Mapper/Repository 实现，复用现有 `iam_session` 表并将 `token_id` 统一映射为 `sessionId`
  - [x] SubTask 1.3: 补齐仓储定向测试，覆盖创建、按 `sessionId` 查询、撤销与续期语义

- [ ] Task 2: 让 token pair 携带并暴露 `sid`
  - [ ] SubTask 2.1: 扩展 token issuer，在登录与 refresh 签发的 `access token`、`refresh token` 中写入 `sid`
  - [ ] SubTask 2.2: 扩展 JWT claims parser / mapper / runtime identity，使 `CurrentUser` 与 refresh token claims 都能读取 `sessionId`
  - [ ] SubTask 2.3: 补齐 token 相关测试，明确无 `sid` 旧 token 返回 `401 AUTH-4012`

- [x] Task 3: 在登录链路创建当前会话
  - [x] SubTask 3.1: 在登录成功后生成唯一 `sessionId` 并创建 `ACTIVE` 会话记录
  - [x] SubTask 3.2: 将 `loginIp/userAgent` 注入当前会话创建流程，并使会话 `expiredAt` 与 `refresh token` 过期时间对齐
  - [x] SubTask 3.3: 补齐登录成功创建会话的 application / WebMvc / repository 定向测试

- [x] Task 4: 在受保护请求与 refresh 流程中校验当前会话
  - [x] SubTask 4.1: 在 `JwtAuthenticationFilter` 中增加 `sid` 对应会话有效性校验，拦截不存在、已撤销或已过期会话
  - [x] SubTask 4.2: 在 refresh 流程中先校验当前会话，再继续重载用户、角色、权限与范围
  - [x] SubTask 4.3: refresh 成功后续期同一会话的 `expiredAt`，并补齐 access/filter/refresh 失败场景测试

- [x] Task 5: 落地 `POST /api/auth/logout` 当前会话撤销接口
  - [x] SubTask 5.1: 新增 logout application service / controller 接口，登录态下从当前认证上下文读取 `sessionId`
  - [x] SubTask 5.2: 按“仅当前会话”语义撤销 `iam_session`，写入 `revokedAt` 并返回 `ApiResponse.success(null)`
  - [x] SubTask 5.3: 补齐 logout WebMvc / application / repository 测试，并验证已撤销会话下 access 与 refresh 都立即失效

- [x] Task 6: 对齐文档、错误语义与最终验证
  - [x] SubTask 6.1: 更新 `api-surface.md` 与 `auth-login-permission-scope-flow.md`，补齐 `sid`、会话校验与 logout 行为说明
  - [x] SubTask 6.2: 复核 `AUTH-4010/AUTH-4011/AUTH-4012` 语义映射，确保会话无效统一返回 `AUTH-4012`
  - [x] SubTask 6.3: 运行定向测试、`compile` 与诊断检查，确认 A-19 闭环

# Task Dependencies
- `Task 2` depends on `Task 1`
- `Task 3` depends on `Task 1`, `Task 2`
- `Task 4` depends on `Task 1`, `Task 2`
- `Task 5` depends on `Task 1`, `Task 2`, `Task 4`
- `Task 6` depends on `Task 3`, `Task 4`, `Task 5`
