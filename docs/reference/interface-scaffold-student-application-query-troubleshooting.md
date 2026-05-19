# 学生查询申请接口常见错误与排查指南

## 1. 适用范围

这份排查指南只针对这条真实链路：

- 接口：`GET /api/student/query/applications`
- Controller：[StudentQueryController](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/StudentQueryController.java)
- Service：[ApplicationQueryApplicationService](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ApplicationQueryApplicationService.java)
- Repository：[MybatisPlusApplicationQueryRepository](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusApplicationQueryRepository.java)
- SQL Provider：[ApplicationQuerySqlProvider](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ApplicationQuerySqlProvider.java)

目标不是列“所有可能错误”，而是覆盖新人最常踩的坑。

## 2. 先用这张表定位问题

| 现象 | 优先怀疑点 |
|---|---|
| 401 未认证 | 请求没带 token，或 `requiredAuthorizationContext()` 取不到用户 |
| 403 无权限 | `@PreAuthorize` 不通过，或 `permissionCode` 传错 |
| 200 但返回空列表 | 范围规则没命中，或业务过滤条件过严 |
| 明明只能看自己的，却查到了别人的数据 | SQL 把业务条件和范围条件拼错了 |
| 接口报 500 | Mapper 参数名不匹配，SQL Provider 取值不对，或 translator 产出异常 |
| 分页不对 | `pageNo/pageSize/offset` 算错 |

## 3. 错误一：忘记加 `@PreAuthorize`

### 现象

- 接口能被任意已登录用户调用
- 甚至在某些情况下没有显式权限也能进应用服务

### 根因

你只写了路由，没有写权限注解。

正确写法：

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_VIEW_SELF)")
@GetMapping("/applications")
public ApiResponse<PageResult<ApplicationRecordView>> pageApplications(...) {
    ...
}
```

### 排查步骤

1. 先看 Controller 方法上有没有 `@PreAuthorize`
2. 再看权限常量是不是 `APPLICATION_VIEW_SELF`
3. 不要只看前端菜单是否隐藏，后端必须显式拦截

### 正确修法

- 学生查询自己的申请，固定用 `AuthorizationPermissionCodes.APPLICATION_VIEW_SELF`

## 4. 错误二：`permissionCode` 传错了

### 现象

- 方法已经加了 `@PreAuthorize`
- 但应用服务里仍然抛 403
- 或同一个接口不同环境行为不一致

### 根因

Controller 传给应用服务的权限码，与注解里的权限码不是一个。

错误示例：

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_VIEW_SELF)")
@GetMapping("/applications")
public ApiResponse<PageResult<ApplicationRecordView>> pageApplications(...) {
    return ApiResponse.success(applicationQueryApplicationService.pageAccessibleApplications(
            new ApplicationPageQuery(...),
            AuthorizationPermissionCodes.APPLICATION_REVIEW
    ));
}
```

正确示例：

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_VIEW_SELF)")
@GetMapping("/applications")
public ApiResponse<PageResult<ApplicationRecordView>> pageApplications(...) {
    return ApiResponse.success(applicationQueryApplicationService.pageAccessibleApplications(
            new ApplicationPageQuery(...),
            AuthorizationPermissionCodes.APPLICATION_VIEW_SELF
    ));
}
```

### 排查步骤

1. 对比 `@PreAuthorize` 用的权限码
2. 对比 `pageAccessibleApplications(..., permissionCode)` 传入的权限码
3. 必须保证两处一致

## 5. 错误三：拿不到当前用户，接口直接 401

### 现象

- 接口返回 401
- 代码在 `requiredAuthorizationContext()` 处报错

### 根因

- 请求没带 Bearer Token
- JWT Filter 没把用户放进 `SecurityContext`
- 你在匿名接口上复用了必须登录的应用服务

业务侧拿上下文的正确方式是：

```java
UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
```

### 排查步骤

1. 确认请求头有 `Authorization: Bearer <token>`
2. 调用 `GET /api/security/me` 看当前用户是否能正常解析
3. 看 [JwtAuthenticationFilter](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-infra/src/main/java/edu/whut/eval/infra/security/jwt/JwtAuthenticationFilter.java) 是否跑通
4. 看是否把匿名接口错误地接到了这条查询链路上

### 正确修法

- 需要登录态的接口，不要绕过 `UserAuthorizationContextAssembler`
- 不要在 Controller 自己手搓 `SecurityContextHolder.getContext().getAuthentication()`

## 6. 错误四：查出来是空列表，但其实不是“没有数据”

### 现象

- 接口返回 200
- `records = []`
- 但数据库里明明有申请数据

### 根因

最常见有三种：

1. 当前用户只有权限，没有命中范围规则
2. `categoryCode`、`itemCode` 等业务过滤条件太严
3. 当前用户的 `SELF` / `ORG_UNIT` / `ORG_SUBTREE` 范围映射错了

### 排查步骤

1. 调 `GET /api/security/me` 看 `scopeRules`
2. 确认这些 `scopeRules` 的 `permissionCode` 是不是 `application.view.self`
3. 看 [DefaultScopePredicateBuilder](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/DefaultScopePredicateBuilder.java) 是否把当前 `scopeType` 转成了申请查询子句
4. 看 [ApplicationScopeSqlTranslator](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-infra/src/main/java/edu/whut/eval/infra/security/sql/ApplicationScopeSqlTranslator.java) 最终生成了什么 SQL 表达式
5. 临时去掉 `categoryCode/itemCode` 等查询参数，再试一次

### 正确理解

- 403 是“根本没权限”
- 200 空列表通常是“有权限，但当前范围下看不到”

不要把这两种情况混成一类。

## 7. 错误五：业务过滤条件和范围条件写成了 `OR`

### 现象

- 本来学生只能看自己的申请
- 结果只要传一个 `categoryCode`，就能看到别人的数据

### 根因

在 `ApplicationQuerySqlProvider` 里把条件拼错了。

错误示例：

```java
sql.append(" WHERE ").append(String.join(" OR ", conditions));
```

正确示例：

```java
sql.append(" WHERE ").append(String.join(" AND ", conditions));
```

### 为什么这是高危问题

因为范围条件决定的是“最多能看什么”，业务过滤只能收窄，不能放大。

正确语义是：

```text
(范围表达式)
AND
(业务过滤表达式)
```

不是：

```text
(范围表达式)
OR
(业务过滤表达式)
```

### 排查步骤

1. 打开 [ApplicationQuerySqlProvider](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ApplicationQuerySqlProvider.java)
2. 看 `String.join(...)` 用的是不是 `AND`
3. 看 `expression` 是否先被作为一个整体条件放进 `conditions`

## 8. 错误六：把 clause 之间的关系也改成了 `AND`

### 现象

- 某个老师明明配了多条范围规则
- 结果查出来的数据比预期少很多，甚至为 0

### 根因

你把 `ApplicationScopeSqlTranslator` 里多个 clause 的关系改成了 `AND`。

真实语义是：

```java
return new SqlPredicateFragment("(" + String.join(" OR ", clauses) + ")", parameters);
```

### 正确理解

- 单条 clause 内部字段是 `AND`
- 多条 clause 之间是 `OR`
- 整个范围表达式与业务条件之间再做 `AND`

### 排查步骤

1. 看 translator 里 `String.join(" OR ", clauses)` 是否被改动
2. 看当前用户是不是有多条 scope rule
3. 看这些 rule 是否本来就应该是并集

## 9. 错误七：Mapper 参数名和 SQL 占位不一致

### 现象

- 接口报 500
- MyBatis 提示找不到参数
- 或 SQL 执行时字段为空

### 根因

`@Param` 名字和 `SQL Provider` 里引用的不一致。

正确 Mapper：

```java
long countAccessibleApplications(@Param("expression") String expression,
                                 @Param("parameters") Map<String, Object> parameters,
                                 @Param("query") ApplicationPageQuery query);
```

正确 SQL Provider：

```java
String expression = params == null ? "" : (String) params.get("expression");
ApplicationPageQuery query = params == null ? null : (ApplicationPageQuery) params.get("query");
```

### 常见错误

- Mapper 里写了 `@Param("pageQuery")`
- SQL Provider 里还在取 `params.get("query")`

### 排查步骤

1. 对照 Mapper 的 `@Param`
2. 对照 SQL Provider 的 `params.get(...)`
3. 两边必须逐字一致

## 10. 错误八：分页参数合法，但分页结果不对

### 现象

- 第 2 页数据和第 1 页重复
- 或翻页后跳过了数据

### 根因

通常是 `offset` 算错了。

正确写法：

```java
public long getOffset() {
    return (pageNo - 1) * pageSize;
}
```

错误写法：

```java
public long getOffset() {
    return pageNo * pageSize;
}
```

### 排查步骤

1. 看 `ApplicationPageQuery#getOffset()`
2. 看 Mapper 调用时是否传了 `query.getOffset()`
3. 看 SQL 是否真用 `LIMIT #{limit} OFFSET #{offset}`

## 11. 错误九：把领域层和 application 层耦死了

### 现象

- 编译依赖开始变乱
- domain 层开始 import application 层对象

### 根因

你跳过了 `ApplicationAccessContext`，直接把 `UserAuthorizationContext` 传进 domain repository。

正确边界是：

```java
ApplicationAccessContext -> ApplicationQueryRepository
```

不是：

```text
UserAuthorizationContext -> Domain Repository
```

### 为什么这很重要

- `UserAuthorizationContext` 属于 application 层
- `ApplicationAccessContext` 是给 domain 查询边界准备的解耦快照

### 正确修法

- application service 负责把 `UserAuthorizationContext` 转成 `ApplicationAccessContext`

## 12. 错误十：把“无权限”和“无数据”都返回 200

### 现象

- 当前用户其实没有权限
- 但为了省事，代码直接 `return new PageResult<>(0, List.of())`

### 根因

错误地把权限拒绝伪装成空列表。

错误示例：

```java
if (!authorizationContext.hasAuthority(permissionCode)) {
    return new PageResult<>(0, List.of());
}
```

正确示例：

```java
if (!authorizationContext.hasAuthority(permissionCode)) {
    throw new AccessDeniedAppException("当前用户无权限访问申请列表");
}
```

### 为什么不能偷懒

- 这样会让调用方误以为“只是没有数据”
- 也会让后续排查权限问题极难定位

## 13. 错误十一：`scopeRules` 的 `permissionCode` 对不上

### 现象

- 用户有角色
- 也看起来有很多 scope rule
- 但申请查询还是只返回空

### 根因

当前请求要求的是：

```text
application.view.self
```

但你查到的范围规则可能挂的是：

```text
application.review
```

或别的权限码。

### 排查步骤

1. 看 `GET /api/security/me` 返回的 `scopeRules`
2. 检查每条 rule 的 `permissionCode`
3. 必须和当前接口传入的 `permissionCode` 一致

### 正确理解

- 范围规则不能脱离权限码单独生效

## 14. 错误十二：明明代码对了，但还是查不到数据

### 现象

- 权限注解对
- 当前用户上下文对
- SQL 拼接看起来也对
- 还是没有结果

### 最后几项检查

1. `application_record` 表里是否真有符合条件的数据
2. `applicant_user_id / org_unit_id / category_code / item_code` 是否与你的范围规则一致
3. `org_path` 的存储格式是否符合 `LIKE %/.../%` 这类匹配方式
4. 当前用户身份是否真的应该走 `SELF`，而不是 `ORG_UNIT` / `ORG_SUBTREE`

### 实用排查顺序

建议按这个顺序排，不要乱跳：

1. `GET /api/security/me`
2. 看 `authorities`
3. 看 `scopeRules`
4. 看 `permissionCode`
5. 看 `ApplicationScopeSqlTranslator`
6. 看 `ApplicationQuerySqlProvider`
7. 最后再看数据库原始数据

## 15. 新人最低成本自查清单

每次改完这条接口，自己过一遍：

1. Controller 上有 `@PreAuthorize`
2. 注解权限码和 service 传参权限码一致
3. `requiredAuthorizationContext()` 能拿到用户
4. `ApplicationAccessContext` 没被删掉
5. `evaluate -> predicate -> translator -> mapper` 顺序没被破坏
6. translator 内 clause 之间还是 `OR`
7. SQL Provider 里范围条件和业务条件还是 `AND`
8. `offset` 计算没写错
9. 403 还是抛异常，不是伪装成空列表
10. 排查时先看 `/api/security/me`，不要上来就怀疑数据库

## 16. 一句话记忆

学生查询申请这条链路，最容易踩坑的地方不是 SQL 本身，而是：

```text
权限码不一致、范围没命中、AND/OR 拼错、以及把无权限误当成无数据。
```
