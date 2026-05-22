# A-19 Task 4 Session Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 access filter 与 refresh 链路补上 `sid` 对应会话有效性校验，并在 refresh 成功后续期同一 `iam_session.expired_at`。

**Architecture:** 把“查询会话 + 判定 ACTIVE/未撤销/未过期 + 续期”收敛到一个 application service，避免 filter 与 refresh 各自复制状态判断。`JwtAuthenticationFilter` 在补齐授权上下文前先校验会话；refresh 在重载用户前校验会话，签发新 token 后再把会话 `expiredAt` 延到新的 refresh token 过期时间。

**Tech Stack:** Java 21, Spring Boot, Spring Security, Spring MVC Test, Mockito, Maven

---

### Task 1: 先写失败测试锁定会话校验行为

**Files:**
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/security/DefaultIamSessionAccessServiceTest.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/security/SecurityProbeControllerWebMvcTest.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/security/AuthControllerWebMvcTest.java`

- [ ] **Step 1: 写会话访问服务失败测试**

```java
@Test
void shouldRejectWhenSessionIsRevoked() {
    given(iamSessionRepository.findBySessionId("sid-revoked"))
            .willReturn(Optional.of(new IamSession(..., IamSessionStatus.REVOKED, ...)));

    assertThatThrownBy(() -> service.assertActive("sid-revoked"))
            .isInstanceOf(JwtAuthenticationException.class)
            .hasMessage("session is invalid");
}
```

再补一个续期测试：

```java
@Test
void shouldExtendSessionExpiration() {
    service.extendExpiration("sid-1001", expiredAt);
    verify(iamSessionRepository).extendExpiration("sid-1001", expiredAt);
}
```

- [ ] **Step 2: 运行单测确认失败**

Run: `mvn -pl whut-eval-app -am -Dtest=DefaultIamSessionAccessServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL，因为 `DefaultIamSessionAccessService` 尚不存在

- [ ] **Step 3: 写 filter 失败测试**

```java
@Test
void shouldReturn4012WhenSessionIsRevoked() throws Exception {
    given(iamSessionAccessService.assertActive("sid-1001"))
            .willThrow(new JwtAuthenticationException("session is invalid"));

    mockMvc.perform(get("/api/security/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + createValidToken()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-4012"));
}
```

并断言：

```java
verify(userAuthorizationContextLoader, never()).load(any());
```

- [ ] **Step 4: 运行单测确认失败**

Run: `mvn -pl whut-eval-app -am -Dtest=SecurityProbeControllerWebMvcTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL，因为 `JwtAuthenticationFilter` 还没调用会话校验

- [ ] **Step 5: 写 refresh 失败测试**

补两个场景：
- 会话失效时 `POST /api/auth/refresh` 返回 `401 AUTH-4012`
- refresh 成功后调用续期，且续期目标时间等于响应中的 `refreshTokenExpiresAt`

```java
verify(iamSessionAccessService).extendExpiration(eq("sid-1001"), any(LocalDateTime.class));
```

- [ ] **Step 6: 运行单测确认失败**

Run: `mvn -pl whut-eval-app -am -Dtest=AuthControllerWebMvcTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL，因为 refresh 还没先校验会话且不会续期

### Task 2: 写最小生产代码让测试转绿

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/IamSessionAccessService.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/DefaultIamSessionAccessService.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/security/jwt/JwtAuthenticationFilter.java`
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/DefaultRefreshTokenCurrentUserLoader.java`
- Modify: `whut-eval-app/src/main/java/edu/whut/eval/app/security/AuthController.java`

- [ ] **Step 1: 新增会话访问服务接口**

```java
public interface IamSessionAccessService {
    void assertActive(String sessionId);
    void extendExpiration(String sessionId, LocalDateTime expiredAt);
}
```

- [ ] **Step 2: 实现统一会话有效性判定**

```java
IamSession session = repository.findBySessionId(sessionId)
        .orElseThrow(() -> new JwtAuthenticationException("session is invalid"));
if (!session.isActive(LocalDateTime.now())) {
    throw new JwtAuthenticationException("session is invalid");
}
```

- [ ] **Step 3: 在 filter 中先校验会话再补齐授权**

```java
CurrentUser tokenUser = jwtClaimsToCurrentUserMapper.map(claims);
iamSessionAccessService.assertActive(tokenUser.getSessionId());
UserAuthorizationContext authorizationContext = userAuthorizationContextLoader.load(...);
```

- [ ] **Step 4: 在 refresh loader 中先校验会话**

```java
iamSessionAccessService.assertActive(context.sessionId());
```

- [ ] **Step 5: 在 refresh 控制器成功后续期**

```java
JwtTokenPair tokenPair = jwtTokenIssuer.issueTokenPair(currentUser);
iamSessionAccessService.extendExpiration(
        subject.getSessionId(),
        LocalDateTime.ofInstant(tokenPair.getRefreshTokenExpiresAt(), ZoneId.systemDefault())
);
```

- [ ] **Step 6: 运行定向测试转绿**

Run: `mvn -pl whut-eval-app -am -Dtest=DefaultIamSessionAccessServiceTest,SecurityProbeControllerWebMvcTest,AuthControllerWebMvcTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS

### Task 3: 回写规格并做编译验证

**Files:**
- Modify: `.trae/specs/implement-a19-logout-session/tasks.md`

- [ ] **Step 1: 更新规格任务勾选**

勾选：
- `Task 4`
- `SubTask 4.1`
- `SubTask 4.2`
- `SubTask 4.3`

- [ ] **Step 2: 运行编译验证**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 运行诊断检查**

检查最近编辑文件无新增实际诊断错误
