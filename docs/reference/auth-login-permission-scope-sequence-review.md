# 认证、权限与可见范围时序图评审版

## 1. 文档定位

本文档是 [认证、权限与可见范围链路说明](./auth-login-permission-scope-flow.md) 的时序图版，面向团队评审场景。

阅读目标：

- 快速看懂当前认证底座已经打通到什么程度
- 快速看懂权限与范围是在哪个阶段查库、在哪个阶段进入运行时上下文
- 快速识别当前授权内核已完成部分与业务接线边界

## 2. 当前结论

当前系统的状态可以先用三句话概括：

1. `login` 已接通真实认证服务，当前按 `user_no + SHA-256 password_hash` 完成账号密码校验并签发 token。
2. `refresh` 已经打通，能重载最新角色、权限、范围并重新签发 token。
3. 普通受保护请求已经会在进入 `SecurityContext` 前按 `userId` 动态补齐最新权限和范围。

---

## 3. 时序图一：当前 Login 闭环

```mermaid
sequenceDiagram
    actor Client as Client
    participant AuthController as AuthController
    participant AuthService as Login Authentication Service
    participant UserRepo as IamUserQueryRepository
    participant RoleRepo as RoleAssignmentQueryRepository
    participant PermRepo as UserAuthorityQueryRepository
    participant ScopeRepo as UserScopeRuleQueryRepository
    participant TokenIssuer as JwtTokenIssuer

    Client->>AuthController: POST /api/auth/login\ncredential + password
    AuthController->>AuthService: authenticate(credential, password)
    AuthService->>UserRepo: findCredentialByUserNo(userNo)
    UserRepo-->>AuthService: IamUserCredential
    AuthService->>AuthService: 校验 SHA-256 password_hash / ACTIVE
    AuthService->>RoleRepo: findActiveAssignmentsByUserId(userId)
    RoleRepo-->>AuthService: roles
    AuthService->>PermRepo: findActivePermissionCodesByUserId(userId)
    PermRepo-->>AuthService: authorities
    AuthService->>ScopeRepo: findActiveScopeRulesByUserId(userId)
    ScopeRepo-->>AuthService: scopeRules
    AuthService-->>AuthController: AuthenticatedUserSnapshot
    AuthController->>TokenIssuer: issueTokenPair(CurrentUser)
    TokenIssuer-->>AuthController: access token + refresh token
    AuthController-->>Client: 200 + token pair
```

### 评审关注点

- 当前 login 和 refresh 共用同一套“查角色/权限/范围 -> 组装 `CurrentUser` -> 签发 token”思路。
- 第一版密码校验按 `SHA-256` 摘要实现，后续如果升级口令算法，需要单独考虑迁移。
- 当前已完成的是认证闭环，后续剩余工作主要是把认证结果接到真实业务流程。

---

## 4. 时序图二：Refresh Token 闭环

```mermaid
sequenceDiagram
    actor Client as Client
    participant AuthController as AuthController
    participant ClaimsParser as JwtClaimsParser
    participant RefreshMapper as RefreshTokenClaimsMapper
    participant RefreshLoader as DefaultRefreshTokenCurrentUserLoader
    participant UserRepo as IamUserQueryRepository
    participant RoleRepo as RoleAssignmentQueryRepository
    participant PermRepo as UserAuthorityQueryRepository
    participant ScopeRepo as UserScopeRuleQueryRepository
    participant TokenIssuer as JwtTokenIssuer

    Client->>AuthController: POST /api/auth/refresh\nrefreshToken
    AuthController->>ClaimsParser: parse(refreshToken)
    ClaimsParser-->>AuthController: Claims
    AuthController->>RefreshMapper: map(claims)
    RefreshMapper-->>AuthController: RefreshTokenSubject(userId,userNo,identity)

    AuthController->>RefreshLoader: load(subject)
    RefreshLoader->>UserRepo: findById(userId)
    UserRepo-->>RefreshLoader: IamUser
    RefreshLoader->>RefreshLoader: 校验 userNo / ACTIVE 状态
    RefreshLoader->>RoleRepo: findActiveAssignmentsByUserId(userId)
    RoleRepo-->>RefreshLoader: roles
    RefreshLoader->>PermRepo: findActivePermissionCodesByUserId(userId)
    PermRepo-->>RefreshLoader: authorities
    RefreshLoader->>ScopeRepo: findActiveScopeRulesByUserId(userId)
    ScopeRepo-->>RefreshLoader: scopeRules
    RefreshLoader-->>AuthController: AuthenticatedUserSnapshot

    AuthController->>TokenIssuer: issueTokenPair(CurrentUser)
    TokenIssuer-->>AuthController: new access token + refresh token
    AuthController-->>Client: 200 + token pair
```

### 评审关注点

- Refresh 只信最小身份，不信权限快照。
- 当前 refresh 后一定会拿到数据库里的最新：
  - `roles`
  - `authorities`
  - `scopeRules`
- 用户禁用、权限撤销、范围规则调整，都会在 refresh 后立即生效。

---

## 5. 时序图三：普通受保护请求认证链路

这是当前普通请求已经真实使用的链路。

```mermaid
sequenceDiagram
    actor Client as Client
    participant Filter as JwtAuthenticationFilter
    participant Resolver as JwtTokenResolver
    participant ClaimsParser as JwtClaimsParser
    participant ClaimsMapper as JwtClaimsToCurrentUserMapper
    participant AuthzLoader as RepositoryUserAuthorizationContextLoader
    participant PermRepo as UserAuthorityQueryRepository
    participant ScopeRepo as UserScopeRuleQueryRepository
    participant SecurityContext as SecurityContextHolder
    participant Controller as Protected Controller

    Client->>Filter: 携带 Access Token 请求受保护接口
    Filter->>Resolver: resolve(request)
    Resolver-->>Filter: token
    Filter->>ClaimsParser: parse(token)
    ClaimsParser-->>Filter: claims
    Filter->>ClaimsMapper: map(claims)
    ClaimsMapper-->>Filter: tokenUser\n(uid,uno,uname,identity,roles,authorities)

    Filter->>AuthzLoader: load(userId,userNo,userName,identity,roles)
    AuthzLoader->>PermRepo: findActivePermissionCodesByUserId(userId)
    PermRepo-->>AuthzLoader: latest authorities
    AuthzLoader->>ScopeRepo: findActiveScopeRulesByUserId(userId)
    ScopeRepo-->>AuthzLoader: latest scopeRules
    AuthzLoader-->>Filter: UserAuthorizationContext

    Filter->>SecurityContext: 写入完整 CurrentUser
    SecurityContext-->>Controller: 当前请求已认证
    Controller-->>Client: 返回业务响应
```

### 评审关注点

- 当前普通请求不是“完全只信 Access Token”。
- 当前真实生效的权限和范围来自数据库运行时补齐，而不是 token 中的旧快照。
- 这样做的代价是每次受保护请求都要多查一次权限和范围，但好处是权限变更更快生效。

---

## 6. 时序图四：业务层读取授权上下文

这是认证层进入业务层的交接点。

```mermaid
sequenceDiagram
    participant SecurityContext as SecurityContextHolder
    participant CurrentUserProvider as CurrentUserProvider
    participant Assembler as SecurityContextUserAuthorizationContextAssembler
    participant AppService as Application Service

    AppService->>Assembler: requiredAuthorizationContext()
    Assembler->>CurrentUserProvider: currentUser()
    CurrentUserProvider->>SecurityContext: getAuthentication()
    SecurityContext-->>CurrentUserProvider: CurrentUser
    CurrentUserProvider-->>Assembler: CurrentUser
    Assembler-->>AppService: UserAuthorizationContext
```

### 评审关注点

- 应用服务后面消费的是 `UserAuthorizationContext`，不应该直接耦合 JWT claims 细节。
- 这一层是后续接 `AuthorizationService`、`ScopeResolver`、查询谓词构建器的稳定入口。

---

## 7. 时序图五：权限与范围计算链路

这条链路已经打通到“生成可消费范围集合”。

```mermaid
sequenceDiagram
    participant AppService as Application Service
    participant Assembler as UserAuthorizationContextAssembler
    participant Evaluator as AuthorizationScopeEvaluator

    AppService->>Assembler: requiredAuthorizationContext()
    Assembler-->>AppService: UserAuthorizationContext
    AppService->>Evaluator: evaluate(context, "application.view.assigned")
    Evaluator->>Evaluator: 检查是否拥有该权限
    Evaluator->>Evaluator: 筛选该权限对应的 scopeRules
    Evaluator->>Evaluator: 过滤 ACTIVE / 排序 / 去重
    Evaluator-->>AppService: AuthorizationScopeSet
```

### 评审关注点

- 范围计算前提永远是“先有权限”。
- `AuthorizationScopeSet` 是“这个权限能作用于哪些范围”的聚合结果，不是最终 SQL。

---

## 8. 时序图六：范围集合转查询条件

这条链路已经打通到“参数化 SQL 片段”，但真实业务仓储还没有正式接线。

```mermaid
sequenceDiagram
    participant AppService as Application Service
    participant Evaluator as AuthorizationScopeEvaluator
    participant Builder as ScopePredicateBuilder
    participant SqlTranslator as ApplicationScopeSqlTranslator

    AppService->>Evaluator: evaluate(context, "application.view.assigned")
    Evaluator-->>AppService: AuthorizationScopeSet
    AppService->>Builder: buildForApplication(context, scopeSet)
    Builder->>Builder: SELF -> applicantUserId
    Builder->>Builder: ORG_UNIT -> orgUnitId
    Builder->>Builder: ITEM -> categoryCode + itemCode
    Builder->>Builder: ORG_UNIT_ITEM -> orgUnitId + categoryCode + itemCode
    Builder->>Builder: ALL -> allowAll
    Builder-->>AppService: ApplicationScopePredicate
    AppService->>SqlTranslator: translate(context, predicate)
    SqlTranslator-->>AppService: SqlPredicateFragment
```

### 评审关注点

- 当前不只产出 `ApplicationScopePredicate`，还可以继续翻译成 `SqlPredicateFragment`。
- 当前设计是“多个 OR 子句”，不是简单的交叉集合。
- 后续真实查询仓储接线时必须保持 `OR` 语义，不能误写成统一 `AND`。

---

## 9. 时序图七：单条资源范围校验

这条链路适用于审批单条申请、查看单条成绩等场景，当前已经落地。

```mermaid
sequenceDiagram
    participant AppService as Application Service
    participant Assembler as UserAuthorizationContextAssembler
    participant Evaluator as ResourceScopeAccessEvaluator
    participant Expr as ScopeRuleExpressionInterpreter

    AppService->>Assembler: requiredAuthorizationContext()
    Assembler-->>AppService: UserAuthorizationContext
    AppService->>Evaluator: canAccessApplication(...) / canAccessScore(...)
    Evaluator->>Evaluator: 匹配 SELF / ORG_UNIT / ORG_SUBTREE / CATEGORY / ITEM / ORG_UNIT_ITEM / ALL
    Evaluator->>Expr: matches(context, expressionJson, resourceContext)
    Expr-->>Evaluator: CUSTOM_EXPRESSION 命中结果
    Evaluator-->>AppService: ScopeAccessDecision
```

### 评审关注点

- 单条资源校验与列表查询复用同一套范围语义，避免两个授权模型分叉。
- `CUSTOM_EXPRESSION` 第一版是受控 JSON DSL，不支持任意脚本表达式。
- 业务在审批或详情场景接线时，应优先复用这个判定器，而不是手写额外 if/else。

---

## 10. 时序图八：完整“可见范围”当前链路

这是当前已经成型的授权内核链路，真实业务查询仓储仍差正式接线。

```mermaid
sequenceDiagram
    actor Client as Client
    participant Filter as JwtAuthenticationFilter
    participant AuthzLoader as RepositoryUserAuthorizationContextLoader
    participant Assembler as UserAuthorizationContextAssembler
    participant Evaluator as AuthorizationScopeEvaluator
    participant Builder as ScopePredicateBuilder
    participant SqlTranslator as ApplicationScopeSqlTranslator
    participant QueryRepo as Application Query Repository
    participant DB as Database

    Client->>Filter: 请求 /review/applications
    Filter->>AuthzLoader: 运行时补齐权限与范围
    AuthzLoader-->>Filter: UserAuthorizationContext
    Filter-->>Assembler: SecurityContext 中已有完整 CurrentUser
    Assembler-->>Evaluator: UserAuthorizationContext
    Evaluator-->>Builder: AuthorizationScopeSet
    Builder-->>SqlTranslator: ApplicationScopePredicate
    SqlTranslator-->>QueryRepo: SqlPredicateFragment
    QueryRepo->>DB: 以参数化 OR 条件查询
    DB-->>QueryRepo: 过滤后的申请数据
    QueryRepo-->>Client: 用户有权可见的数据
```

### 评审关注点

- 授权内核层面的最后一跳已经补齐，当前缺口变成真实申请/成绩查询仓储尚未正式消费 translator 输出。
- 一旦业务查询仓储接线，申请列表和成绩列表就能直接复用当前授权底座。

---

## 11. 当前已完成与边界

### 11.1 已完成

- 真实登录认证服务
- Access / Refresh Token 分离
- Refresh 时全量查库重载
- 普通请求运行时权限补齐
- 普通请求运行时范围补齐
- `CurrentUser -> UserAuthorizationContext` 装配
- `scopeRules -> AuthorizationScopeSet` 评估
- `AuthorizationScopeSet -> ApplicationScopePredicate` 转换
- `AuthorizationScopeSet -> ScoreScopePredicate` 转换
- `ApplicationScopePredicate -> SqlPredicateFragment` 翻译
- `ScoreScopePredicate -> SqlPredicateFragment` 翻译
- 单条资源范围校验
- `CUSTOM_EXPRESSION` 受控 JSON DSL 解释器

### 11.2 当前边界

- 真实申请查询仓储尚未正式消费 `ApplicationScopeSqlTranslator`
- 真实成绩查询仓储尚未正式消费 `ScoreScopeSqlTranslator`
- 组织树相关能力仍依赖 `org_path` 语义，后续是否抽象专门查询能力待业务接线时确定
- `CUSTOM_EXPRESSION` 当前只支持第一版 JSON DSL，后续扩展需保持运行时解释与 SQL 翻译语义一致

---

## 12. 评审建议问题清单

团队评审时建议重点讨论下面 6 个问题：

1. 普通请求是否接受“每次查库补齐权限与范围”的性能成本？
2. 是否需要在后续加入短期缓存，降低 `UserAuthorityQueryRepository` / `UserScopeRuleQueryRepository` 查询频率？
3. `roles` 是否也要像 `authorities` 一样改成请求期重载？
4. 真实申请/成绩查询仓储如何统一消费 translator 产出的 `SqlPredicateFragment`？
5. `ORG_SUBTREE` 是否继续依赖 `org_path`，还是需要单独引入组织树查询能力？
6. `CUSTOM_EXPRESSION` 下一版是否要增加更多运算符，还是继续保持最小 DSL？

---

## 13. 一句话评审结论

当前项目已经完成：

- 真实登录
- 认证底座
- 权限重载
- 范围重载
- 范围解释
- 范围到查询谓词转换
- 范围到参数化 SQL 片段翻译
- 单条资源范围判定

当前项目当前边界：

- 真实业务查询仓储接线
- 业务审批/详情接口正式复用授权判定器

所以从评审角度看，当前最适合进入下一阶段的是：

- 把申请查询正式接到 `ApplicationScopeSqlTranslator`
- 而不是继续扩展新的认证模型分支
