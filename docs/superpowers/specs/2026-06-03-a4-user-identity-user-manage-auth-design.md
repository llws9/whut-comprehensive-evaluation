# A-4 设计说明：身份查询补齐 user.manage 鉴权

- 日期：2026-06-03
- 目标项：A-4 `/api/iam/users/{userNo}/identity` 增加 `user.manage` 权限校验
- 关联清单：`docs/team-delivery/group-a-identity-user-admin-relay-checklist.md`

---

## 1. 背景与目标

当前接口 `GET /api/iam/users/{userNo}/identity` 可返回用户身份聚合信息，但控制器缺少 `@PreAuthorize`，导致与组文档 A-4 的权限契约不一致。

本次目标：

1. 为身份查询接口补齐 `user.manage` 方法级鉴权；
2. 无权限请求返回 403 语义；
3. 有权限请求行为保持不变（继续返回当前 identity 数据结构）。

---

## 2. 范围

### In Scope

- `UserIdentityQueryController#getUserIdentity` 增加 `@PreAuthorize`。
- 增加安全注解契约测试（反射方式，校验注解和值）。
- 增加 WebMvc 鉴权契约测试（403/200）。
- 更新 A 组接力清单 A-4 状态与完成说明（实施完成后）。

### Out of Scope

- 不改 `UserIdentityQueryApplicationService` 业务逻辑。
- 不改 identity 返回字段结构。
- 不改 JWT 解析链路与全局安全配置。

---

## 3. 方案与选型

### 方案 A（采用）：控制器方法级鉴权 + 双层测试（注解契约 + WebMvc 契约）

- 在 controller 方法上直接加 `@PreAuthorize("hasAuthority(T(...).USER_MANAGE)")`。
- 用反射测试保障注解值不回退。
- 用 WebMvc 测试保障“无权限=403，有权限=200”。

**优点**：改动小、与现有 `UserAdminControllerSecurityAnnotationTest` 风格一致、可快速闭环。  
**缺点**：WebMvc 测试需显式准备 security context。

### 方案 B：仅做注解反射测试

**优点**：实现最轻。  
**缺点**：只能证明“写了注解”，无法证明 403 运行时语义。

---

## 4. 目标改动文件

- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserIdentityQueryController.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserIdentityQueryControllerSecurityAnnotationTest.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserIdentityQueryControllerWebMvcTest.java`
- Modify（实施完成后）: `docs/team-delivery/group-a-identity-user-admin-relay-checklist.md`

---

## 5. 测试策略

### 5.1 注解契约测试（静态）

校验 `UserIdentityQueryController#getUserIdentity(String)` 上存在：

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)")
```

并校验：

```java
AuthorizationPermissionCodes.USER_MANAGE.equals("user.manage")
```

### 5.2 WebMvc 契约测试（运行时）

新增两类场景：

1. **无权限（只有其他权限）**：返回 `403`；
2. **有 `user.manage` 权限**：返回 `200` 且成功体结构不变。

---

## 6. 验收清单（A-4）

- [ ] `UserIdentityQueryController#getUserIdentity` 已加 `user.manage` 的 `@PreAuthorize`。
- [ ] 无权限请求 `/api/iam/users/{userNo}/identity` 返回 403。
- [ ] 有权限请求返回 200，且 identity 响应结构保持兼容。
- [ ] A-4 相关测试全部通过。
- [ ] 接力清单 A-4 状态更新为 `[x]` 并写明完成说明。

---

## 7. 最小执行命令（实施时）

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=UserIdentityQueryControllerSecurityAnnotationTest,UserIdentityQueryControllerWebMvcTest test
```

扩展回归：

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=UserAdminControllerSecurityAnnotationTest test
```
