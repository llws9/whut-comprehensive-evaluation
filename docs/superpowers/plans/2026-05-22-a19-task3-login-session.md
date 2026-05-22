# A-19 Task 3 Login Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在登录成功后创建一条 `ACTIVE` 的 `iam_session` 记录，并保存 `sessionId/loginIp/userAgent/expiredAt`，同时补齐定向测试、编译验证与规格任务勾选。

**Architecture:** 保持 HTTP 适配细节留在 `AuthController`，由它提取 `X-Forwarded-For`、`User-Agent` 并生成 `sessionId`。新增一个轻量 application service 负责把登录后的会话快照持久化到 `iam_session`，`expiredAt` 直接对齐 `JwtTokenPair.refreshTokenExpiresAt`，避免重复计算 TTL 与业务漂移。

**Tech Stack:** Java 17, Spring Boot, Spring MVC Test, Mockito, MyBatis Plus, Maven

---

### Task 1: 先写失败测试锁定行为

**Files:**
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/security/DefaultLoginAuthenticationServiceTest.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/security/AuthControllerWebMvcTest.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/MybatisPlusIamSessionRepositoryIntegrationTest.java`

- [ ] **Step 1: 写 application service 失败测试**

```java
@Test
void shouldCreateActiveSession() {
    // 断言 save() 收到 ACTIVE 会话，且字段完整
}
```

- [ ] **Step 2: 运行单测确认失败**

Run: `mvn -pl whut-eval-app -Dtest=DefaultLoginSessionCommandServiceTest test`
Expected: FAIL，因为 `DefaultLoginSessionCommandService` 尚不存在

- [ ] **Step 3: 写 WebMvc 失败测试**

```java
.header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
.header("User-Agent", "JUnit-Agent")
```

断言会话创建命令中：
- `loginIp = 203.0.113.10`
- `userAgent = JUnit-Agent`
- `expiredAt` 与响应中 refresh token 解析出的过期时间一致

- [ ] **Step 4: 运行单测确认失败**

Run: `mvn -pl whut-eval-app -Dtest=AuthControllerWebMvcTest test`
Expected: FAIL，因为控制器还没有创建会话

- [ ] **Step 5: 补 repository 断言**

```java
assertThat(found.loginIp()).isEqualTo("127.0.0.1");
assertThat(found.userAgent()).isEqualTo("JUnit");
assertThat(found.expiredAt()).isEqualTo(now.plusHours(8));
```

- [ ] **Step 6: 运行单测确认失败或缺口存在**

Run: `mvn -pl whut-eval-app -Dtest=MybatisPlusIamSessionRepositoryIntegrationTest test`
Expected: FAIL 或现有断言不足，补到能锁定持久化字段

### Task 2: 写最小生产代码让测试转绿

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/auth/model/LoginSessionCreateCommand.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/LoginSessionCommandService.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/DefaultLoginSessionCommandService.java`
- Modify: `whut-eval-app/src/main/java/edu/whut/eval/app/security/AuthController.java`

- [ ] **Step 1: 新增登录会话创建命令模型**

```java
public record LoginSessionCreateCommand(
        Long userId,
        String sessionId,
        String loginIp,
        String userAgent,
        LocalDateTime expiredAt
) {
}
```

- [ ] **Step 2: 新增 application service 并落库为 ACTIVE**

```java
IamSession session = new IamSession(
        null,
        command.userId(),
        command.sessionId(),
        command.loginIp(),
        command.userAgent(),
        command.expiredAt(),
        null,
        IamSessionStatus.ACTIVE,
        LocalDateTime.now()
);
repository.save(session);
```

- [ ] **Step 3: 在控制器串联发 token 与建会话**

```java
String sessionId = UUID.randomUUID().toString();
JwtTokenPair tokenPair = jwtTokenIssuer.issueTokenPair(currentUser);
loginSessionCommandService.create(new LoginSessionCreateCommand(...));
```

并新增两个私有 helper：
- `resolveLoginIp(HttpServletRequest request)`
- `resolveUserAgent(HttpServletRequest request)`

- [ ] **Step 4: 运行定向测试转绿**

Run: `mvn -pl whut-eval-app -Dtest=DefaultLoginSessionCommandServiceTest,AuthControllerWebMvcTest,MybatisPlusIamSessionRepositoryIntegrationTest test`
Expected: PASS

### Task 3: 回归验证与任务勾选

**Files:**
- Modify: `.trae/specs/implement-a19-logout-session/tasks.md`

- [ ] **Step 1: 更新规格任务勾选**

勾选：
- `Task 3`
- `SubTask 3.1`
- `SubTask 3.2`
- `SubTask 3.3`

- [ ] **Step 2: 运行编译验证**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 运行诊断检查**

检查最近编辑文件无新增诊断错误
