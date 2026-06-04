# A-4 User Identity Authorization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `/api/iam/users/{userNo}/identity` 补齐 `user.manage` 鉴权，确保无权限返回 403、有权限保持 200 与响应兼容。

**Architecture:** 在 `UserIdentityQueryController#getUserIdentity` 增加方法级 `@PreAuthorize`，权限常量复用 `AuthorizationPermissionCodes.USER_MANAGE`。测试分两层：一层做反射注解契约（防止注解值回退），一层做 WebMvc 运行时契约（403/200）。保持应用服务与响应 DTO 不变，遵循最小改动。

**Tech Stack:** Java 21+, Spring Boot 3.3, Spring Security Method Security, JUnit 5, MockMvc, Maven Surefire。

---

## File Structure (before tasks)

- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserIdentityQueryController.java`
  - 在身份查询方法上补 `@PreAuthorize`。
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserIdentityQueryControllerSecurityAnnotationTest.java`
  - 反射校验 `@PreAuthorize` 注解和值。
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserIdentityQueryControllerWebMvcTest.java`
  - 增加 403/200 鉴权契约测试，并开启 security filter。
- Modify: `docs/team-delivery/group-a-identity-user-admin-relay-checklist.md`
  - A-4 状态改为已完成并补充完成说明与接力记录。

---

### Task 1: 控制器补 user.manage 鉴权注解

**Files:**
- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserIdentityQueryController.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserIdentityQueryControllerSecurityAnnotationTest.java`

- [ ] **Step 1: 先写安全注解契约失败测试（红）**

Create `UserIdentityQueryControllerSecurityAnnotationTest.java`:

```java
package edu.whut.eval.app.iam;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.interfaces.iam.UserIdentityQueryController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class UserIdentityQueryControllerSecurityAnnotationTest {

    @Test
    void shouldRequireUserManageAuthorityOnIdentityEndpoint() throws NoSuchMethodException {
        Method method = UserIdentityQueryController.class.getMethod(
                "getUserIdentity",
                String.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)"
        );
        assertThat(AuthorizationPermissionCodes.USER_MANAGE).isEqualTo("user.manage");
    }
}
```

- [ ] **Step 2: 运行该测试确认失败**

Run:
```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=UserIdentityQueryControllerSecurityAnnotationTest test
```
Expected: FAIL（`preAuthorize` 为 null）。

- [ ] **Step 3: 控制器补最小实现（绿）**

Modify `UserIdentityQueryController.java` imports + method:

```java
import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)")
@GetMapping("/{userNo}/identity")
public ApiResponse<UserIdentityView> getUserIdentity(@PathVariable String userNo) {
    return ApiResponse.success(userIdentityQueryApplicationService.getUserIdentityByUserNo(userNo));
}
```

- [ ] **Step 4: 重跑注解测试确认通过**

Run:
```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=UserIdentityQueryControllerSecurityAnnotationTest test
```
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add \
  whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserIdentityQueryController.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserIdentityQueryControllerSecurityAnnotationTest.java

git commit -m "feat(iam): enforce user.manage on user identity query"
```

**Rollback point:** 回滚该 commit 后，A-4 鉴权注解契约恢复为未生效状态。

---

### Task 2: WebMvc 增加 403/200 运行时鉴权契约

**Files:**
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserIdentityQueryControllerWebMvcTest.java`

- [ ] **Step 1: 先加运行时鉴权失败用例（红）**

在测试类添加 import：

```java
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
```

并新增两个测试（先写）：

```java
@Test
void shouldReturn403WhenUserLacksUserManageAuthority() throws Exception {
    mockMvc.perform(get("/api/iam/users/2024305001/identity")
                    .with(SecurityMockMvcRequestPostProcessors.user("viewer").authorities(() -> "user.read")))
            .andExpect(status().isForbidden());
}

@Test
void shouldReturn200WhenUserHasUserManageAuthority() throws Exception {
    userIdentityQueryApplicationService.willReturn(new UserIdentityView(
            new IamUser(1010L, "2024305001", "王老师", "w@example.com", "13800000000", "ACTIVE"),
            List.of(),
            List.of()
    ));

    mockMvc.perform(get("/api/iam/users/2024305001/identity")
                    .with(SecurityMockMvcRequestPostProcessors.user("admin").authorities(() -> "user.manage")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.user.userNo").value("2024305001"));
}
```

- [ ] **Step 2: 打开 security filter 让鉴权真正生效**

把测试类注解：

```java
@AutoConfigureMockMvc(addFilters = false)
```

改为：

```java
@AutoConfigureMockMvc
```

- [ ] **Step 3: 运行 WebMvc 测试确认从红到绿**

Run:
```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=UserIdentityQueryControllerWebMvcTest test
```
Expected:
- 在 Step 1（注解未补前）可能 FAIL；
- 完成 Task 1 + Step 2 后 PASS。

- [ ] **Step 4: 回归已有 identity 响应冻结字段测试**

Run:
```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=UserIdentityQueryControllerWebMvcTest#shouldKeepFrozenMembershipFieldsInIdentityResponse test
```
Expected: PASS（响应结构不变）。

- [ ] **Step 5: Commit**

```bash
git add whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserIdentityQueryControllerWebMvcTest.java

git commit -m "test(iam): add 403 and 200 auth contract for identity query"
```

**Rollback point:** 回滚该 commit 后仅丢失 A-4 运行时鉴权契约测试，不影响生产逻辑。

---

### Task 3: 文档更新与最终验收

**Files:**
- Modify: `docs/team-delivery/group-a-identity-user-admin-relay-checklist.md`

- [ ] **Step 1: 更新 A-4 状态与完成说明**

将 A-4 状态改为 `[x]`，完成说明补：

```markdown
- `GET /api/iam/users/{userNo}/identity` 增加 `user.manage` 方法级鉴权
- 无权限访问返回 403，有权限访问保持 200 与原响应结构
- 最小验证：UserIdentityQueryControllerSecurityAnnotationTest / UserIdentityQueryControllerWebMvcTest
```

并在“接力记录”追加：

```markdown
| 2026-06-03 | Claude | A-4 | 身份查询接口补 user.manage 鉴权与 403 契约 |
```

- [ ] **Step 2: 运行 A-4 定向测试集**

Run:
```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=UserIdentityQueryControllerSecurityAnnotationTest,UserIdentityQueryControllerWebMvcTest test
```
Expected: BUILD SUCCESS。

- [ ] **Step 3: 运行扩展安全回归**

Run:
```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=UserAdminControllerSecurityAnnotationTest test
```
Expected: BUILD SUCCESS（确保未破坏既有鉴权契约测试）。

- [ ] **Step 4: 生成交付摘要（用于 PR 描述）**

```markdown
## A-4 验证结果
- 身份查询接口补齐 user.manage 鉴权
- 无权限访问返回 403
- 有权限访问返回 200 且响应结构兼容

## 测试
- UserIdentityQueryControllerSecurityAnnotationTest: PASS
- UserIdentityQueryControllerWebMvcTest: PASS
- UserAdminControllerSecurityAnnotationTest: PASS
```

- [ ] **Step 5: Commit**

```bash
git add docs/team-delivery/group-a-identity-user-admin-relay-checklist.md

git commit -m "docs(team): mark A-4 completed with auth verification notes"
```

**Rollback point:** 回滚本任务仅影响文档与交付记录，不影响运行时。

---

## 测试计划总览

1. 注解契约测试：反射确保 `@PreAuthorize(user.manage)` 不回退。
2. WebMvc 契约测试：验证无权限 403、有权限 200。
3. 兼容性测试：冻结字段响应结构保持不变。
4. 扩展安全回归：既有 `UserAdminController` 权限注解测试仍通过。

---

## 回滚策略

- 代码级回滚顺序：Task 3 → Task 2 → Task 1。
- 快速止损：若生产出现误杀，可先回滚 Task 1（去除新注解）恢复旧行为。
- 测试回滚：Task 2/3 可独立回滚，不影响核心逻辑。

---

## 验收清单

- [ ] `UserIdentityQueryController#getUserIdentity` 存在 `user.manage` 的 `@PreAuthorize`。
- [ ] 无 `user.manage` 权限访问返回 HTTP 403。
- [ ] 有 `user.manage` 权限访问返回 HTTP 200。
- [ ] Identity 响应冻结字段契约保持兼容。
- [ ] A-4 定向测试与扩展安全回归全部通过。
- [ ] 接力清单 A-4 状态更新为已完成并附完成说明。

---

## Spec Coverage Self-Review

- 覆盖项 1（补注解）：Task 1。
- 覆盖项 2（403/200 运行时）：Task 2。
- 覆盖项 3（文档与验收记录）：Task 3。
- Placeholder 检查：无 TBD/TODO。
- 命名一致性：统一使用 `USER_MANAGE` / `user.manage`。
