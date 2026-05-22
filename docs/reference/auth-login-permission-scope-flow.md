# 认证、权限与可见范围链路说明

配套评审版时序图文档：

- [认证、权限与可见范围时序图评审版](./auth-login-permission-scope-sequence-review.md)

## 1. 文档目的

本文档说明当前 `rewrite/whut-comprehensive-evaluation` 中已经落地的三条核心链路：

1. 登录 / Refresh Token / Logout 链路
2. 权限查询链路
3. 用户可见范围链路

同时会明确当前实现边界，避免把“已完成的认证底座”误认为“完整登录业务已经接通”。

## 2. 当前状态总览

截至当前实现，系统状态如下：

- `POST /api/auth/login` 已接入真实账号密码认证服务，当前按 `user_no + SHA-256 password_hash` 完成认证，并在登录成功后生成 `sid`、创建 `ACTIVE iam_session`、再签发 Token。
- `POST /api/auth/refresh` 已经打通，可以基于携带 `sid` 的 Refresh Token 校验当前会话、重载最新用户、角色、权限、范围，并重新签发同一会话下的新 Token。
- `POST /api/auth/logout` 已落地，当前语义是“仅撤销当前会话”，不会影响同一用户其他设备或其他会话。
- 普通受保护请求已经打通 Access Token 解析、`sid` 会话校验、运行时权限补齐、范围规则补齐、`SecurityContext` 装配。
- 业务侧已经可以从 `SecurityContext` 读取完整 `UserAuthorizationContext`。
- 业务侧已经可以把“某权限下的范围规则”转换成 `AuthorizationScopeSet`，再转换成 `ApplicationScopePredicate` / `ScoreScopePredicate` 这样的查询条件对象。
- 单条资源范围校验已经支持申请和成绩两类资源，并覆盖 `SELF`、`ORG_UNIT`、`ORG_SUBTREE`、`CATEGORY`、`ITEM`、`ORG_UNIT_ITEM`、`CUSTOM_EXPRESSION`、`ALL`。
- 查询层参数化 SQL translator 已完成，但尚未把它正式接入真实申请/成绩查询仓储。

一句话总结：

- 当前已经完成“真实登录 + 可撤销会话 + 认证与授权底座 + 范围解释 + SQL translator”。
- 当前尚未完成“真实业务查询仓储/接口对这些授权产物的正式接线”。

## 3. 核心对象

当前链路里最重要的对象有 8 个：

### 3.1 `CurrentUser`

`CurrentUser` 是放入 `SecurityContext` 的运行时认证主体，当前包含：

- `userId`
- `userNo`
- `userName`
- `identity`
- `sessionId`
- `roles`
- `authorities`
- `scopeRules`

说明：

- `scopeRules` 不放入 JWT。
- `scopeRules` 是请求期或 refresh 期从数据库动态补齐的。
- `sessionId` 来自 JWT `sid` claim，并与 `iam_session.token_id` 共用同一业务语义。

### 3.2 `UserAuthorizationContext`

`UserAuthorizationContext` 是业务侧消费的授权上下文，通常由 `SecurityContextUserAuthorizationContextAssembler` 从 `CurrentUser` 转换而来。

它的职责不是做认证，而是给应用服务提供稳定、可直接使用的：

- 当前用户身份
- 当前会话 ID
- 当前权限集合
- 当前范围规则集合

### 3.3 `AuthenticatedUserSnapshot`

`AuthenticatedUserSnapshot` 是登录与 refresh 重载链路中的中间快照对象，用于承载数据库中重新加载出来的完整用户信息。

### 3.4 `AuthorizationScopeSet`

`AuthorizationScopeSet` 表示：

- 某个权限码在当前用户上下文下，最终可用的范围集合

它解决的是：

- “这个人有没有 `application.view.assigned`”
- “如果有，这个权限可作用于哪些范围”

### 3.5 `ApplicationScopePredicate`

`ApplicationScopePredicate` 表示：

- 某个权限对应的申请查询条件集合

注意它不是最终 SQL，而是查询层可消费的谓词对象。

### 3.6 `UserAuthorityQueryRepository`

用于按 `userId` 查询当前用户有效权限码集合。

当前查询来源是：

- `iam_user_role_assignment`
- `iam_role`
- `iam_role_permission`
- `iam_permission`

### 3.7 `UserScopeRuleQueryRepository`

用于按 `userId` 查询当前用户有效范围规则集合。

当前查询来源是：

- `iam_scope_rule`
- `iam_user_role_assignment`
- `iam_role`
- `iam_permission`
- `iam_role_permission`

### 3.8 `IamSessionAccessService`

用于按 `sessionId` 校验当前会话是否仍然可用，并在 refresh 成功后续期同一会话。

当前职责是：

- `assertActive(sessionId)`：拒绝不存在、已撤销、已过期或空白 `sid`
- `extendExpiration(sessionId, expiredAt)`：把当前会话的 `expiredAt` 续到新 Refresh Token 的过期时间

## 4. Token 设计

当前采用 Access Token / Refresh Token 分离设计。

### 4.1 Access Token

Access Token 当前包含：

- `token_type=access`
- `uid`
- `uno`
- `uname`
- `identity`
- `sid`
- `roles`
- `authorities`

设计意图：

- 用于普通请求快速完成基础身份解析。
- 允许网关或过滤器先完成初步认证。
- 让 Access Token 与服务端 `iam_session` 建立一一关联，从而支持“服务端立即失效”。

但当前实现又增加了一步“运行时补齐”，所以：

- Access Token 中虽然带有 `authorities`
- 真正进入 `SecurityContext` 前，系统仍会按 `userId` 从数据库重新加载最新 `authorities` 和 `scopeRules`

这意味着：

- Access Token 既保留了轻量快照能力
- 又降低了权限变化后的滞后风险

### 4.2 Refresh Token

Refresh Token 当前只包含：

- `token_type=refresh`
- `uid`
- `uno`
- `identity`
- `sid`

设计意图：

- Refresh Token 只承担“重新换发”的最小身份职责
- 不携带 `userName`
- 不携带 `roles`
- 不携带 `authorities`
- 不携带 `scopeRules`
- 但必须携带 `sid`，否则无法建立服务端会话校验闭环

因此 refresh 时一定要重新查库，并先校验 `sid` 对应会话仍然有效。

补充说明：

- Access Token 与 Refresh Token 必须携带同一个 `sid`。
- 不带 `sid` 的旧 token 当前统一视为非法 token，返回 `401 AUTH-4012`。

## 5. 登录链路

### 5.1 当前真实状态

当前 `POST /api/auth/login` 已接入真实认证，当前流程是：

1. 控制器接收 `credential/password`
2. `LoginAuthenticationService` 归一化账号并按 `user_no` 查询用户凭证
3. `PasswordHashVerifier` 以 SHA-256 校验输入密码与 `iam_user.password_hash`
4. 校验用户状态是否为 `ACTIVE`
5. 查询有效角色分配
6. 查询有效权限
7. 查询有效范围规则
8. 组装 `AuthenticatedUserSnapshot`
9. 转成完整 `CurrentUser`
10. 生成唯一 `sessionId`
11. 调用 `JwtTokenIssuer.issueTokenPair(...)`，在 Access Token / Refresh Token 中都写入同一个 `sid`
12. 调用 `LoginSessionCommandService#create(...)` 创建 `ACTIVE iam_session`
13. 返回 Access Token + Refresh Token

### 5.2 登录链路的当前实现边界

当前登录已是可运行闭环，但仍有两个边界需要明确：

1. 第一版密码方案按 `SHA-256` 摘要校验实现，后续如需升级为更强口令算法，需要单独做存量兼容迁移。
2. 当前登录创建的 `iam_session` 是“单会话单行”模型，当前只支持撤销当前会话，不包含多设备管理界面。
3. 当前文档讨论的是认证与授权内核，尚未把登录成功后的用户能力进一步接到具体业务首页或聚合视图。

## 6. Refresh Token 链路

Refresh 链路当前已经是完整闭环。

### 6.1 流程图

```text
POST /api/auth/refresh
  -> AuthController.refresh()
  -> JwtClaimsParser.parse()
  -> RefreshTokenClaimsMapper.map()
  -> IamSessionAccessService.assertActive()
  -> RefreshTokenCurrentUserLoader.load()
  -> JwtTokenIssuer.issueTokenPair()
  -> IamSessionAccessService.extendExpiration()
  -> 返回新 token pair
```

### 6.2 详细步骤

1. `AuthController.refresh()` 接收 `refreshToken`
2. `JwtClaimsParser` 校验签名、issuer、audience、过期时间
3. `RefreshTokenClaimsMapper` 校验 `token_type=refresh`
4. 解析出最小身份：
   - `userId`
   - `userNo`
   - `identity`
   - `sessionId`
5. `IamSessionAccessService.assertActive(sessionId)` 校验会话存在、未撤销且未过期
6. `DefaultRefreshTokenCurrentUserLoader` 开始查库重载
7. 通过 `IamUserQueryRepository` 查询用户
8. 校验：
   - `userNo` 是否一致
   - 用户状态是否为 `ACTIVE`
9. 通过 `RoleAssignmentQueryRepository` 查询有效角色分配
10. 通过 `UserAuthorityQueryRepository` 查询有效权限
11. 通过 `UserScopeRuleQueryRepository` 查询有效范围规则
12. 组装 `AuthenticatedUserSnapshot`
13. 转成新的 `CurrentUser`，沿用原来的 `sessionId`
14. `JwtTokenIssuer` 签发新的 Access Token / Refresh Token
15. `IamSessionAccessService.extendExpiration(...)` 续期当前会话
16. 返回给客户端

### 6.3 这条链路的特点

Refresh 链路的核心特征是：

- 不信任 Refresh Token 中的权限和范围
- 一律重新查库

因此：

- 用户被禁用后，refresh 会失败
- 用户权限被撤销后，新签发 token 会立刻反映变化
- 用户范围规则变更后，新签发 token 也会立刻反映变化
- 当前会话被撤销或过期后，refresh 会直接失败

### 6.4 `AUTH-4010/AUTH-4011/AUTH-4012` 当前口径

当前 A-19 链路里的 3 个认证错误码，真实实现语义如下：

- `AUTH-4010`：凭证认证失败，或 token 通过基础 JWT 校验后，在查库阶段发现用户主体不一致 / 用户状态不是 `ACTIVE`
- `AUTH-4011`：错误码仍保留在 `CommonErrorCode` 中，但当前 login / refresh / filter / logout 链路没有直接返回它
- `AUTH-4012`：所有 JWT / session 非法场景的统一口径，包括签名错误、issuer/audience 不匹配、`token_type` 错误、缺少 `sid`、旧 token 缺少必填 claim、JWT 过期、会话不存在、会话已撤销、会话已过期

## 7. 普通受保护请求链路

这里指的是类似：

- `GET /api/security/me`
- 未来的申请查询、审核接口、成绩查询接口

### 7.1 流程图

```text
HTTP Request
  -> JwtAuthenticationFilter
  -> JwtTokenResolver
  -> JwtClaimsParser
  -> JwtClaimsToCurrentUserMapper
  -> IamSessionAccessService.assertActive()
  -> UserAuthorizationContextLoader
  -> CurrentUser 写入 SecurityContext
  -> 业务层读取 UserAuthorizationContext
```

### 7.2 详细步骤

1. `JwtAuthenticationFilter` 拦截请求
2. `JwtTokenResolver` 从 Header / Cookie / Query 中提取 token
3. `JwtClaimsParser` 做 JWT 基础校验
4. `JwtClaimsToCurrentUserMapper` 从 Access Token 中读取：
   - `uid`
   - `uno`
   - `uname`
   - `identity`
   - `sid`
   - `roles`
   - `authorities`
5. 得到一个“仅基于 token claims 的轻量 `CurrentUser`”
6. `IamSessionAccessService.assertActive(sessionId)` 校验当前会话仍有效
7. `RepositoryUserAuthorizationContextLoader` 再根据 `userId` 查库补齐：
   - 最新 `authorities`
   - 最新 `scopeRules`
8. 把查库结果组装成 `UserAuthorizationContext`
9. 再转成最终 `CurrentUser`
10. 写入 `SecurityContext`
11. 后续控制器 / 应用服务通过 `CurrentUserProvider` 或 `UserAuthorizationContextAssembler` 读取

### 7.3 这条链路的关键结论

当前普通请求不是“完全只信 JWT”。

它实际是：

- 用 Access Token 完成基础身份识别
- 先用 `iam_session` 完成服务端会话有效性校验
- 再用数据库完成权限和范围的运行时补齐

所以当前运行时真正生效的是“数据库会话状态 + 数据库授权状态”，而不是 token 中旧的 `authorities` 快照。

### 7.4 当前会话登出链路

`POST /api/auth/logout` 的实现目标不是“让前端删 token”，而是让服务端立即拒绝当前会话。

处理流程：

1. `JwtAuthenticationFilter` 先完成 Access Token 解析与当前会话有效性校验
2. `CurrentUserProvider.requiredCurrentUser()` 读取带 `sessionId` 的当前主体
3. `LogoutSessionCommandService.logout(sessionId)` 调用 `IamSessionRepository.revoke(sessionId, now)`
4. 数据库把当前会话状态改成 `REVOKED` 并写入 `revokedAt`
5. 接口返回 `ApiResponse.success(null)`
6. 同一 `sid` 下后续 Access Token 与 Refresh Token 都会在会话校验阶段返回 `401 AUTH-4012`

## 8. 权限查询链路

权限查询链路主要发生在两个地方：

1. Refresh Token 重载
2. 普通受保护请求的运行时补齐

### 8.1 查询入口

统一入口是：

- `UserAuthorityQueryRepository.findActivePermissionCodesByUserId(userId)`

### 8.2 当前查询逻辑

当前权限查询按“有效角色分配 -> 有效角色 -> 有效角色权限 -> 有效权限”这条链路做关联查询。

简化后的语义是：

1. 找到该用户所有 `ACTIVE` 且在有效期内的角色分配
2. 过滤掉失效角色
3. 过滤掉失效权限
4. 汇总得到去重后的权限码集合

### 8.3 权限在系统中的使用方式

权限集合当前主要有三种用途：

1. 写入 `CurrentUser.authorities`
2. 转成 Spring Security `GrantedAuthority`
3. 给 `AuthorizationScopeEvaluator` 判定“是否具备该权限”

也就是说，范围计算前提是：

- 先有权限
- 再算范围

## 9. 范围规则查询链路

### 9.1 查询入口

统一入口是：

- `UserScopeRuleQueryRepository.findActiveScopeRulesByUserId(userId)`

### 9.2 当前查询逻辑

当前范围规则查询会关联：

- `iam_scope_rule`
- `iam_user_role_assignment`
- `iam_role`
- `iam_permission`
- `iam_role_permission`

语义是：

1. 只取该用户有效角色分配下的规则
2. 只取 `ACTIVE` 的 scope rule
3. 只保留“当前角色确实具备该权限”的规则
4. 最后按 `priority` 等字段排序返回

### 9.3 范围规则在系统中的位置

范围规则当前会被装配到：

- `AuthenticatedUserSnapshot.scopeRules`
- `CurrentUser.scopeRules`
- `UserAuthorizationContext.scopeRules`

这意味着：

- 认证层能看见 scope rules
- 授权层能看见 scope rules
- 后续查询层也能看见 scope rules

## 10. 用户可见范围链路

这里的“用户可见范围”指的是：

- 某个权限下，当前用户到底能看到哪些申请 / 成绩 / 审核任务

当前已经完成四步：

1. 范围规则解释
2. 范围规则转查询谓词
3. 查询谓词转参数化 SQL 片段
4. 单条资源范围命中校验

当前尚未完成的是：

5. 把这些授权产物正式接入真实申请/成绩查询仓储与接口

### 10.1 第一步：范围规则解释

入口是：

- `AuthorizationScopeEvaluator.evaluate(context, permissionCode)`

输入：

- `UserAuthorizationContext`
- 某个权限码，例如 `application.view.assigned`

输出：

- `AuthorizationScopeSet`

它做的事情是：

1. 先检查用户是否拥有该权限
2. 如果没有，返回 `denied`
3. 如果有，筛出该权限对应的 `scopeRules`
4. 过滤非 `ACTIVE` 规则
5. 归一化成 `AuthorizationScope`
6. 去重、排序
7. 返回可用范围集合

### 10.2 第二步：范围集合转查询条件

入口是：

- `ScopePredicateBuilder.buildForApplication(context, scopeSet)`

输出：

- `ApplicationScopePredicate`

它会把不同的范围类型翻译成查询可消费的条件子句：

- `SELF` -> `applicantUserId = currentUser.userId`
- `ALL` -> 无条件放行
- `ORG_UNIT` -> `orgUnitId`
- `ORG_SUBTREE` -> `orgSubtreeRootId`
- `CATEGORY` -> `categoryCode`
- `ITEM` -> `categoryCode + itemCode`
- `ORG_UNIT_ITEM` -> `orgUnitId + categoryCode + itemCode`
- `CUSTOM_EXPRESSION` -> 保留 `expressionJson`，交给受控 JSON DSL 解释器或 SQL translator 继续处理

### 10.3 为什么是“子句集合”而不是“简单集合”

这里特意设计成 `ApplicationScopeClause` 列表，而不是简单的：

- `orgUnitIds`
- `itemCodes`

原因是当前范围语义本质上是多个 `OR` 子句，不是简单交集。

例如：

- 规则 A：`ORG_UNIT(classA)`
- 规则 B：`ITEM(ACADEMIC_LECTURE)`

正确语义是：

- 申请属于 `classA`
- 或 申请属于 `ACADEMIC_LECTURE`

不能错误翻译成：

- `orgUnitId in (...) AND itemCode in (...)`

所以 SQL translator 必须按“多个 clause 进行 OR 拼接”消费这个谓词对象，真实业务仓储接线时也必须保持这个语义。

## 11. 从请求到查询条件的完整路径

以“用户请求申请列表”为例，完整路径应该是：

```text
客户端携带 Access Token 请求 /review/applications
  -> JwtAuthenticationFilter 完成认证
  -> IamSessionAccessService.assertActive(sid)
  -> RepositoryUserAuthorizationContextLoader 补齐最新 authorities/scopeRules
  -> SecurityContext 中得到完整 CurrentUser
  -> 业务层通过 UserAuthorizationContextAssembler 读取 UserAuthorizationContext
  -> AuthorizationScopeEvaluator.evaluate(context, "application.view.assigned")
  -> 得到 AuthorizationScopeSet
  -> ScopePredicateBuilder.buildForApplication(...)
  -> 得到 ApplicationScopePredicate
  -> Repository/Mapper 把 ApplicationScopePredicate 转成 SQL WHERE 条件
  -> 查询数据库
  -> 返回用户有权看到的数据
```

## 12. 单条资源范围校验链路

除列表查询外，当前还支持对单条资源做范围命中校验，适用于“审批某一条申请”“查看某一条成绩详情”这类场景。

### 12.1 统一入口

- `ResourceScopeAccessEvaluator.canAccessApplication(...)`
- `ResourceScopeAccessEvaluator.canAccessScore(...)`

### 12.2 处理流程

1. 业务层读取 `UserAuthorizationContext`
2. 评估某权限码对应的 `AuthorizationScopeSet`
3. 将资源对象映射为 `ApplicationResourceContext` 或 `ScoreResourceContext`
4. 逐条匹配范围规则
5. 命中即返回 `ScopeAccessDecision.allow(...)`
6. 全部未命中则返回 `ScopeAccessDecision.deny(...)`

### 12.3 `CUSTOM_EXPRESSION` 的当前语义

`CUSTOM_EXPRESSION` 第一版不是任意脚本，而是受控 JSON DSL，当前支持：

- 根节点 `allOf`
- 条件字段 `field`
- 操作符 `EQ`
- 操作符 `IN`
- 固定值 `value` / `values`
- 动态取值 `valueFrom=currentUser.userId|userNo|identity`

这意味着：

- 资源命中时可以按资源字段与当前用户上下文做参数化比较
- 不允许执行任意 SpEL、Groovy、JavaScript 或 SQL 片段
- 解释器和 SQL translator 使用同一套 DSL 约束，避免运行时语义漂移

## 13. 当前边界与未完成项

### 13.1 已完成

- JWT 基础认证
- 登录成功创建 `ACTIVE iam_session`
- 真实登录认证服务
- Access / Refresh Token 分离
- Access / Refresh Token 都携带 `sid`
- Refresh 时全量查库重载
- 普通请求运行时会话校验
- Refresh 前置会话校验与成功后续期
- 当前会话登出与服务端撤销
- 普通请求运行时权限补齐
- 普通请求运行时范围补齐
- 业务授权上下文装配
- 范围规则解释
- 范围规则转申请查询谓词
- 范围规则转成绩查询谓词
- `ApplicationScopePredicate` 到参数化 SQL 片段的翻译
- `ScoreScopePredicate` 到参数化 SQL 片段的翻译
- 单条申请/成绩资源范围校验
- `CUSTOM_EXPRESSION` 受控 JSON DSL 解释器

### 13.2 当前边界

- 申请查询接口本身尚未正式消费 `ApplicationScopeSqlTranslator`
- 成绩查询接口本身尚未正式消费 `ScoreScopeSqlTranslator`
- 组织树相关能力当前仍基于 `org_path` 前缀/子树语义，是否引入独立组织树仓储仍待后续业务落地决定
- `CUSTOM_EXPRESSION` 当前只支持第一版受控 JSON DSL，若后续需要更丰富运算符，需扩展解释器与 SQL translator 的对齐语义
- `AUTH-4011` 当前只在错误码枚举中保留，若后续需要把“JWT 过期”与“JWT 非法”拆成不同口径，需要单独调整 parser / entry point 映射

## 14. 推荐后续落地顺序

建议后续按下面顺序继续推进：

1. 把申请列表查询正式接到 `ApplicationScopeSqlTranslator`
2. 把成绩列表查询正式接到 `ScoreScopeSqlTranslator`
3. 在审批、详情等场景正式接入 `ResourceScopeAccessEvaluator`
4. 补齐组织树真实查询能力或统一 `org_path` 约束
5. 视业务需要扩展 `CUSTOM_EXPRESSION` DSL 运算符

## 15. 一句话总结

当前系统已经完成的是：

- 一套可运行的真实登录认证链路
- 一套可撤销、可校验、可续期的当前会话链路
- 一套可运行的 JWT 认证底座
- 一套可运行的权限加载链路
- 一套可运行的范围规则加载与解释链路
- 一套已经能产出查询条件对象、SQL 片段和单条资源访问判定的授权模型

后续业务模块主要工作已经不是“再补认证内核”，而是把这些授权产物接到真实查询和审批流程里做正式复用。
