# A-19 当前会话登出与 Session 持久化闭环 Spec

## Why
当前认证链路已经具备 `login + access token + refresh token` 基础能力，但仍是纯无状态 JWT 方案，服务端无法主动撤销已签发 token，导致 `logout` 只能依赖前端删除本地 token。`A-19` 的问题本质不是补一个退出接口，而是引入可持久化、可撤销、可校验的当前会话模型，让当前 `access token` 与 `refresh token` 能在服务端即时失效。

## What Changes
- 新增 `A-19` 的服务端会话模型：复用 `iam_session` 作为“单会话行”存储，一条记录代表一次登录会话。
- 统一将 `iam_session.token_id` 解释为 `sessionId`，并要求 `access token` 与 `refresh token` 同时携带 `sid` claim。
- 登录成功后创建 `ACTIVE` 会话记录，并以 `refresh token` 的过期时间作为会话到期时间。
- 刷新 token 时新增会话有效性校验，并在刷新成功后延长当前会话的 `expiredAt`。
- 普通受保护请求在 JWT 解析后新增当前会话校验；会话不存在、已撤销或已过期时立即拒绝访问。
- 新增 `POST /api/auth/logout`，仅撤销当前会话，不扩展为“全部设备登出”。
- 新增 `IamSession` 领域模型、`IamSessionRepository` 仓储契约及对应持久化实现与定向测试。
- 旧 token 兼容策略按最短闭环处理：不携带 `sid` 的旧 token 统一视为非法并要求重新登录。

## Impact
- Affected specs: `A-1`、`A-2`、`A-19`
- Affected code: `whut-eval-app`、`whut-eval-application`、`whut-eval-domain`、`whut-eval-infra`、`docs/reference/api-surface.md`、`docs/reference/auth-login-permission-scope-flow.md`

## ADDED Requirements
### Requirement: Persistent Current Session
系统 SHALL 以 `iam_session` 作为当前登录会话的唯一持久化模型，一条记录代表一次登录会话，而不是分别为 `access token` 与 `refresh token` 建立独立存储。

#### Scenario: Create session on successful login
- **WHEN** 用户通过 `POST /api/auth/login` 完成认证
- **THEN** 系统生成唯一 `sessionId`
- **AND** 系统创建一条 `iam_session` 记录，状态为 `ACTIVE`
- **AND** 该记录至少保存 `userId/sessionId/loginIp/userAgent/expiredAt/status/createdAt`
- **AND** `expiredAt` 与本次签发的 `refresh token` 过期时间保持一致

### Requirement: Session-Aware Token Pair
系统 SHALL 为当前会话签发带 `sid` claim 的 `access token` 与 `refresh token`，并在运行时将 `sid` 作为服务端会话校验的唯一定位键。

#### Scenario: Issue token pair with sid
- **WHEN** 登录成功或 refresh 成功后重新签发 token pair
- **THEN** `access token` 必须包含 `sid`
- **AND** `refresh token` 必须包含 `sid`
- **AND** 同一当前会话内重新签发的 token pair 复用同一个 `sessionId`

#### Scenario: Reject legacy token without sid
- **WHEN** 服务端收到不包含 `sid` 的旧 token
- **THEN** 系统将其视为非法 token
- **AND** 返回 `401 AUTH-4012`

### Requirement: Session Validation For Protected Requests
系统 SHALL 在受保护请求的 JWT 解析之后、装配授权上下文之前校验当前会话是否仍然有效。

#### Scenario: Accept request when session is active
- **WHEN** `access token` 签名、时效与 `sid` 均合法，且 `iam_session` 对应记录存在、未撤销、未过期
- **THEN** 系统继续加载最新 `authorities` 与 `scopeRules`
- **AND** 请求进入后续鉴权与业务链路

#### Scenario: Reject request when session is revoked
- **WHEN** `access token` 中的 `sid` 对应会话不存在、已撤销或已过期
- **THEN** 系统拒绝本次请求
- **AND** 返回 `401 AUTH-4012`

### Requirement: Session Validation And Extension For Refresh
系统 SHALL 在 refresh 流程中校验 `refresh token` 所属当前会话，并在刷新成功后延长该会话有效期。

#### Scenario: Refresh token with active session
- **WHEN** 客户端调用 `POST /api/auth/refresh` 且 `refresh token` 签名、类型与 `sid` 合法
- **AND** `sid` 对应会话仍为 `ACTIVE` 且 `expiredAt` 晚于当前时间
- **THEN** 系统重载最新用户、角色、权限与范围
- **AND** 重新签发新的 token pair
- **AND** 使用新的 `refresh token` 过期时间更新同一会话的 `expiredAt`

#### Scenario: Reject refresh when session is revoked
- **WHEN** 客户端调用 `POST /api/auth/refresh`，但 `sid` 对应会话不存在、已撤销或已过期
- **THEN** 系统拒绝刷新
- **AND** 返回 `401 AUTH-4012`

### Requirement: Logout Current Session
系统 SHALL 提供仅撤销当前会话的 `POST /api/auth/logout`，使当前会话下的 `access token` 与 `refresh token` 立即失效。

#### Scenario: Logout current session successfully
- **WHEN** 已认证用户调用 `POST /api/auth/logout`
- **AND** 当前 `access token` 中携带合法 `sid`
- **THEN** 系统将对应 `iam_session` 标记为 `REVOKED`
- **AND** 写入 `revokedAt`
- **AND** 返回 `ApiResponse.success(null)`

#### Scenario: Reject logout when session is already invalid
- **WHEN** 已认证用户调用 `POST /api/auth/logout`，但当前 `sid` 对应会话不存在、已撤销或已过期
- **THEN** 系统拒绝本次登出
- **AND** 返回 `401 AUTH-4012`

### Requirement: Minimal Session State Model
系统 SHALL 以最小状态集合表达当前会话生命周期，避免引入与本轮目标无关的设备管理或 token family 复杂度。

#### Scenario: Use minimal session states
- **WHEN** 实现 `A-19`
- **THEN** 会话状态只使用 `ACTIVE` 与 `REVOKED`
- **AND** 过期通过 `expiredAt <= now` 运行时判定
- **AND** 本轮不得扩展为“全部设备登出”、`refresh token rotation family` 或独立 token 黑名单系统

## MODIFIED Requirements
### Requirement: Auth Runtime Context
系统 SHALL 在运行时身份模型中保留 `sessionId`，使普通请求、refresh 与 logout 都能基于同一当前会话口径工作。

#### Scenario: Map sid into runtime identity
- **WHEN** JWT 解析成功
- **THEN** 系统将 `sid` 映射进运行时 `CurrentUser` 或等价认证上下文对象
- **AND** refresh token claims 模型也必须暴露 `sessionId`

### Requirement: Auth Error Semantics
系统 SHALL 将“token 非法”与“会话已失效”统一收敛到现有认证错误语义，而不是引入新的错误码体系。

#### Scenario: Reuse existing auth codes
- **WHEN** token 缺失 `sid`、`sid` 不存在、会话已撤销或会话已过期
- **THEN** 系统统一返回 `401 AUTH-4012`
- **AND** refresh token 自身时间过期仍返回 `401 AUTH-4011`
- **AND** 用户状态非 `ACTIVE` 仍返回 `401 AUTH-4010`

## REMOVED Requirements
- 无
