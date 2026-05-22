# A-19 Task 5 Logout Current Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `POST /api/auth/logout`，仅撤销当前登录会话，写入 `revokedAt`，返回 `ApiResponse.success(null)`，并补齐定向测试与规格勾选。

**Architecture:** `AuthController` 通过 `CurrentUserProvider` 从当前认证上下文读取 `sessionId`，不直接操作仓储。新增一个很薄的 `LogoutSessionCommandService` 负责按“仅当前会话”语义调用 `IamSessionRepository.revoke(sessionId, now)`，既复用已有会话模型，也保持 controller 只做请求编排。

**Tech Stack:** Java 21, Spring Boot, Spring Security, Spring MVC Test, Mockito, MyBatis Plus, Maven

---

### Task 1: 先写失败测试锁定 logout 行为

**Files:**
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/security/DefaultLogoutSessionCommandServiceTest.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/security/AuthControllerWebMvcTest.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/MybatisPlusIamSessionRepositoryIntegrationTest.java`

- [ ] **Step 1: 写 application service 失败测试**

```java
@Test
void shouldRevokeCurrentSession() {
    service.logout("sid-logout");
    verify(iamSessionRepository).revoke(eq("sid-logout"), any(LocalDateTime.class));
}
```

- [ ] **Step 2: 运行单测确认失败**

Run: `mvn -pl whut-eval-app -am -Dtest=DefaultLogoutSessionCommandServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL，因为 `DefaultLogoutSessionCommandService` 尚不存在

- [ ] **Step 3: 写 WebMvc 失败测试**

```java
given(currentUserProvider.requiredCurrentUser()).willReturn(currentUser());

mockMvc.perform(post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + createLogoutAccessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.data").doesNotExist());

verify(logoutSessionCommandService).logout("sid-logout");
```

- [ ] **Step 4: 运行单测确认失败**

Run: `mvn -pl whut-eval-app -am -Dtest=AuthControllerWebMvcTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL，因为 `/api/auth/logout` 尚不存在

- [ ] **Step 5: 补 repository 失败测试**

```java
repository.revoke("sid-current", now);

assertThat(repository.findBySessionId("sid-current")).hasValueSatisfying(found -> {
    assertThat(found.status()).isEqualTo(IamSessionStatus.REVOKED);
    assertThat(found.revokedAt()).isEqualTo(now);
});
assertThat(repository.findBySessionId("sid-other")).hasValueSatisfying(found ->
        assertThat(found.status()).isEqualTo(IamSessionStatus.ACTIVE));
```

- [ ] **Step 6: 运行单测确认失败或补足断言缺口**

Run: `mvn -pl whut-eval-app -am -Dtest=MybatisPlusIamSessionRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL 或现有断言不足，补到能锁定“仅当前会话撤销”

### Task 2: 写最小生产代码让测试转绿

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/LogoutSessionCommandService.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/DefaultLogoutSessionCommandService.java`
- Modify: `whut-eval-app/src/main/java/edu/whut/eval/app/security/AuthController.java`

- [ ] **Step 1: 新增 logout application service 接口**

```java
public interface LogoutSessionCommandService {
    void logout(String sessionId);
}
```

- [ ] **Step 2: 实现最小撤销逻辑**

```java
@Service
public class DefaultLogoutSessionCommandService implements LogoutSessionCommandService {
    @Override
    public void logout(String sessionId) {
        iamSessionRepository.revoke(sessionId, LocalDateTime.now());
    }
}
```

- [ ] **Step 3: 在控制器新增 `/logout`**

```java
@PostMapping("/logout")
public ApiResponse<Void> logout() {
    CurrentUser currentUser = currentUserProvider.requiredCurrentUser();
    logoutSessionCommandService.logout(currentUser.getSessionId());
    return ApiResponse.success(null);
}
```

- [ ] **Step 4: 运行定向测试转绿**

Run: `mvn -pl whut-eval-app -am -Dtest=DefaultLogoutSessionCommandServiceTest,AuthControllerWebMvcTest,MybatisPlusIamSessionRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS

### Task 3: 回写规格并做编译验证

**Files:**
- Modify: `.trae/specs/implement-a19-logout-session/tasks.md`

- [ ] **Step 1: 更新规格任务勾选**

勾选：
- `Task 5`
- `SubTask 5.1`
- `SubTask 5.2`
- `SubTask 5.3`

- [ ] **Step 2: 运行编译验证**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 运行诊断检查**

检查最近编辑文件无新增实际诊断错误
