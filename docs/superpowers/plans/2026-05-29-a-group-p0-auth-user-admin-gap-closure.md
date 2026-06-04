# A组 P0 缺口补齐（Logout + 用户管理主链路）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐 A 组 P0 缺失能力：A-19 logout、A-5/A-6/A-7 用户管理接口，并确保关键鉴权与会话撤销行为可回归测试。

**Architecture:** 保持现有分层结构（interfaces → application → domain/infra），在最小改动下新增用户管理 command 路径和 logout 路径。logout 复用现有 `IamSessionRepository` 与 `SessionRevocationService`；用户状态更新在应用层触发会话撤销，避免在 controller 写业务逻辑。每个提交都以 TDD 驱动，先写失败测试再实现。

**Tech Stack:** Java 21, Spring Boot, Spring Security, MyBatis-Plus, JUnit 5, MockMvc, Maven

---

## File Structure（按职责拆分）

- `whut-eval-app/src/main/java/edu/whut/eval/app/security/AuthController.java`
  - 负责 auth HTTP 入口；新增 logout 路由。
- `whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/LogoutService.java`
  - logout 应用服务接口。
- `whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/DefaultLogoutService.java`
  - logout 应用服务实现；解析 token id 并撤销 session。
- `whut-eval-app/src/test/java/edu/whut/eval/app/security/AuthControllerWebMvcTest.java`
  - auth controller 的 WebMvc 回归测试（新增 logout case）。
- `whut-eval-app/src/test/java/edu/whut/eval/app/security/DefaultLogoutServiceTest.java`
  - logout 应用服务单测。
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserAdminController.java`
  - 用户管理 HTTP 入口（A-5/A-6/A-7）。
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/CreateUserRequest.java`
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/UpdateUserStatusRequest.java`
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/response/UserPageItemResponse.java`
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/response/UserCreatedResponse.java`
  - 用户管理接口 DTO。
- `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/CreateUserCommand.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/UpdateUserStatusCommand.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/UserAdminPageQuery.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/UserAdminPageItemView.java`
  - 用户管理应用层模型与用例。
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/IamUserCommandRepository.java`
  - 用户写仓储契约（create / updateStatus）。
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamUserCommandRepository.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/IamUserMapper.java`
  - 用户写路径的 infra 实现。
- `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerWebMvcTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerSecurityAnnotationTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminApplicationServiceTest.java`
  - 用户管理 Web 层、鉴权注解、应用层测试。

---

### Task 1: Commit 1 - 先写 logout 失败测试（Web + Service）

**Files:**
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/security/AuthControllerWebMvcTest.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/security/DefaultLogoutServiceTest.java`

- [ ] **Step 1: 在 AuthControllerWebMvcTest 新增 logout 失败测试**

```java
@Test
void shouldReturn401WhenLogoutWithoutToken() throws Exception {
    mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-4012"));
}
```

- [ ] **Step 2: 新建 DefaultLogoutServiceTest，先写一个失败用例**

```java
@Test
void shouldRevokeSessionByAccessTokenId() {
    IamSessionRepository repository = mock(IamSessionRepository.class);
    SessionRevocationService revocationService = new SessionRevocationService(repository);
    DefaultLogoutService service = new DefaultLogoutService(repository, revocationService);

    when(repository.findByAccessTokenId("access-jti")).thenReturn(
            new IamSession(1L, "S-1", 1001L, "access-jti", "refresh-jti", "WEB", "127.0.0.1", null, null, IamSession.SessionStatus.ACTIVE, null)
    );

    boolean ok = service.logoutByAccessTokenId("access-jti");

    assertTrue(ok);
    verify(repository).revokeById(1L);
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `mvn -pl whut-eval-app -Dtest=AuthControllerWebMvcTest,DefaultLogoutServiceTest test`

Expected: FAIL（`/api/auth/logout` endpoint 不存在，`DefaultLogoutService` 类不存在）

- [ ] **Step 4: Commit（只提交测试）**

```bash
git add whut-eval-app/src/test/java/edu/whut/eval/app/security/AuthControllerWebMvcTest.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/security/DefaultLogoutServiceTest.java
git commit -m "test: add failing tests for auth logout flow"
```

---

### Task 2: Commit 1 - 实现 logout 最小代码直到测试通过

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/LogoutService.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/DefaultLogoutService.java`
- Modify: `whut-eval-app/src/main/java/edu/whut/eval/app/security/AuthController.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/security/AuthControllerWebMvcTest.java`

- [ ] **Step 1: 新增 LogoutService 接口**

```java
package edu.whut.eval.application.auth.service;

public interface LogoutService {
    boolean logoutByAccessTokenId(String accessTokenId);
}
```

- [ ] **Step 2: 新增 DefaultLogoutService 实现**

```java
package edu.whut.eval.application.auth.service;

import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import org.springframework.stereotype.Service;

@Service
public class DefaultLogoutService implements LogoutService {

    private final IamSessionRepository sessionRepository;
    private final SessionRevocationService sessionRevocationService;

    public DefaultLogoutService(IamSessionRepository sessionRepository,
                                SessionRevocationService sessionRevocationService) {
        this.sessionRepository = sessionRepository;
        this.sessionRevocationService = sessionRevocationService;
    }

    @Override
    public boolean logoutByAccessTokenId(String accessTokenId) {
        if (accessTokenId == null || accessTokenId.isBlank()) {
            return false;
        }
        IamSession session = sessionRepository.findByAccessTokenId(accessTokenId);
        if (session == null) {
            return false;
        }
        return sessionRevocationService.revokeSession(session.getId(), "logout");
    }
}
```

- [ ] **Step 3: 在 AuthController 增加 logout endpoint**

```java
@PostMapping("/logout")
public ResponseEntity<ApiResponse<?>> logout(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
        return ResponseEntity.status(CommonErrorCode.INVALID_TOKEN.httpStatus())
                .body(ApiResponse.failure(CommonErrorCode.INVALID_TOKEN, "token 无效或缺失"));
    }
    boolean revoked = logoutService.logoutByAccessTokenId(currentUser.getAccessTokenId());
    if (!revoked) {
        return ResponseEntity.status(CommonErrorCode.INVALID_TOKEN.httpStatus())
                .body(ApiResponse.failure(CommonErrorCode.INVALID_TOKEN, "会话不存在或已失效"));
    }
    return ResponseEntity.ok(ApiResponse.success(null));
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -pl whut-eval-app -Dtest=AuthControllerWebMvcTest,DefaultLogoutServiceTest test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/LogoutService.java \
        whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/DefaultLogoutService.java \
        whut-eval-app/src/main/java/edu/whut/eval/app/security/AuthController.java
git commit -m "feat: add logout endpoint and session revocation"
```

---

### Task 3: Commit 2 - 用户分页/创建先写失败测试

**Files:**
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerWebMvcTest.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerSecurityAnnotationTest.java`

- [ ] **Step 1: 新建 UserAdminControllerWebMvcTest 并写分页失败测试**

```java
@Test
void shouldReturnPagedUsers() throws Exception {
    given(userAdminApplicationService.pageUsers(any()))
            .willReturn(new PageResult<>(1, List.of(
                    new UserAdminPageItemView(1010L, "2024305001", "王老师", "ACTIVE", List.of("计算机学院"), List.of("COUNSELOR"), "2026-05-01T10:00:00")
            )));

    mockMvc.perform(get("/api/admin/users").param("pageNo", "1").param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].userNo").value("2024305001"));
}
```

- [ ] **Step 2: 同文件写创建失败测试**

```java
@Test
void shouldCreateUser() throws Exception {
    given(userAdminApplicationService.createUser(any()))
            .willReturn(new UserCreatedView(1011L, "2024305111", "李老师", "ACTIVE"));

    mockMvc.perform(post("/api/admin/users")
                    .contentType(APPLICATION_JSON)
                    .content("""
                            {"userNo":"2024305111","userName":"李老师","password":"secret123","email":"li@example.com","phone":"13800001111"}
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value(1011));
}
```

- [ ] **Step 3: 新建安全注解测试（先失败）**

```java
@Test
void shouldRequireUserManageAuthorityOnPageAndCreate() throws Exception {
    Method page = UserAdminController.class.getMethod("pageUsers", long.class, long.class, String.class, String.class, Long.class);
    Method create = UserAdminController.class.getMethod("createUser", CreateUserRequest.class);

    PreAuthorize pageAuth = page.getAnnotation(PreAuthorize.class);
    PreAuthorize createAuth = create.getAnnotation(PreAuthorize.class);

    assertEquals("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)", pageAuth.value());
    assertEquals("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)", createAuth.value());
}
```

- [ ] **Step 4: 运行测试确认失败**

Run: `mvn -pl whut-eval-app -Dtest=UserAdminControllerWebMvcTest,UserAdminControllerSecurityAnnotationTest test`

Expected: FAIL（`UserAdminController` 和相关 view/request 尚不存在）

- [ ] **Step 5: Commit（只提交失败测试）**

```bash
git add whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerWebMvcTest.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerSecurityAnnotationTest.java
git commit -m "test: add failing tests for user admin page and create"
```

---

### Task 4: Commit 2 - 实现 A-5/A-6（DTO + service + controller + repo）

**Files:**
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserAdminController.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/CreateUserRequest.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/response/UserPageItemResponse.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/response/UserCreatedResponse.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/CreateUserCommand.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/UserAdminPageQuery.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/UserAdminPageItemView.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/IamUserCommandRepository.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamUserCommandRepository.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/IamUserMapper.java`

- [ ] **Step 1: 写 request/response DTO**

```java
public class CreateUserRequest {
    @NotBlank private String userNo;
    @NotBlank private String userName;
    @NotBlank private String password;
    private String email;
    private String phone;
    @Positive private Long primaryOrgUnitId;
    // getters/setters
}
```

```java
public class UserCreatedResponse {
    private final Long userId;
    private final String userNo;
    private final String userName;
    private final String status;
    // constructor + getters
}
```

- [ ] **Step 2: 写 UserAdminApplicationService 最小逻辑（page/create）**

```java
@Service
public class UserAdminApplicationService {
    private final IamUserQueryRepository userQueryRepository;
    private final IamUserCommandRepository userCommandRepository;

    public PageResult<UserAdminPageItemView> pageUsers(UserAdminPageQuery query) { /* map + return */ }

    public UserCreatedView createUser(CreateUserCommand command) {
        userQueryRepository.findByUserNo(command.userNo()).ifPresent(u -> {
            throw new BusinessConflictException("用户编号已存在: " + command.userNo());
        });
        return userCommandRepository.createUser(command);
    }
}
```

- [ ] **Step 3: 实现 UserAdminController 的 GET/POST**

```java
@RestController
@RequestMapping("/api/admin/users")
@Validated
public class UserAdminController {

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)")
    @GetMapping
    public ApiResponse<PageResult<UserPageItemResponse>> pageUsers(...) { ... }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)")
    @PostMapping
    public ApiResponse<UserCreatedResponse> createUser(@Valid @RequestBody CreateUserRequest request) { ... }
}
```

- [ ] **Step 4: 实现 IamUserCommandRepository + MybatisPlusIamUserCommandRepository**

```java
public interface IamUserCommandRepository {
    UserCreatedView createUser(CreateUserCommand command);
    boolean updateStatus(Long userId, String status);
}
```

```java
@Repository
public class MybatisPlusIamUserCommandRepository implements IamUserCommandRepository {
    @Override
    public UserCreatedView createUser(CreateUserCommand command) {
        IamUserDO entity = new IamUserDO();
        entity.setUserNo(command.userNo());
        entity.setUserName(command.userName());
        entity.setPasswordHash(command.passwordHash());
        entity.setEmail(command.email());
        entity.setPhone(command.phone());
        entity.setStatus("ACTIVE");
        iamUserMapper.insert(entity);
        return new UserCreatedView(entity.getId(), entity.getUserNo(), entity.getUserName(), entity.getStatus());
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -pl whut-eval-app -Dtest=UserAdminControllerWebMvcTest,UserAdminControllerSecurityAnnotationTest test`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserAdminController.java \
        whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/CreateUserRequest.java \
        whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/response/UserPageItemResponse.java \
        whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/response/UserCreatedResponse.java \
        whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java \
        whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/CreateUserCommand.java \
        whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/UserAdminPageQuery.java \
        whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/UserAdminPageItemView.java \
        whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/IamUserCommandRepository.java \
        whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamUserCommandRepository.java \
        whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/IamUserMapper.java
git commit -m "feat: add user admin page and create endpoints"
```

---

### Task 5: Commit 3 - 先写 A-7 状态更新失败测试

**Files:**
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerWebMvcTest.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminApplicationServiceTest.java`

- [ ] **Step 1: 在 UserAdminControllerWebMvcTest 增加 PATCH 失败测试**

```java
@Test
void shouldUpdateUserStatus() throws Exception {
    mockMvc.perform(patch("/api/admin/users/1010/status")
                    .contentType(APPLICATION_JSON)
                    .content("""
                            {"status":"DISABLED","reason":"manual disable"}
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
}
```

- [ ] **Step 2: 新建 UserAdminApplicationServiceTest，写会话撤销失败测试**

```java
@Test
void shouldRevokeSessionsWhenStatusBecomesDisabled() {
    IamUserQueryRepository queryRepository = mock(IamUserQueryRepository.class);
    IamUserCommandRepository commandRepository = mock(IamUserCommandRepository.class);
    SessionRevocationService revocationService = mock(SessionRevocationService.class);

    UserAdminApplicationService service = new UserAdminApplicationService(queryRepository, commandRepository, revocationService);
    when(commandRepository.updateStatus(1010L, "DISABLED")).thenReturn(true);

    service.updateStatus(1010L, new UpdateUserStatusCommand("DISABLED", "manual disable"));

    verify(revocationService).revokeAllActiveSessions(1010L, "user_disabled");
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `mvn -pl whut-eval-app -Dtest=UserAdminControllerWebMvcTest,UserAdminApplicationServiceTest test`

Expected: FAIL（PATCH endpoint / service method 未实现）

- [ ] **Step 4: Commit（只提交失败测试）**

```bash
git add whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerWebMvcTest.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminApplicationServiceTest.java
git commit -m "test: add failing tests for user status update and session revocation"
```

---

### Task 6: Commit 3 - 实现 A-7 状态更新并接入会话撤销

**Files:**
- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserAdminController.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/UpdateUserStatusRequest.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/UpdateUserStatusCommand.java`
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`
- Modify: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/IamUserCommandRepository.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamUserCommandRepository.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/IamUserMapper.java`

- [ ] **Step 1: 增加 UpdateUserStatusRequest 和 command**

```java
public class UpdateUserStatusRequest {
    @NotBlank
    @Pattern(regexp = "ACTIVE|DISABLED|LOCKED")
    private String status;
    private String reason;
    // getters/setters
}
```

```java
public record UpdateUserStatusCommand(String status, String reason) {}
```

- [ ] **Step 2: 在 UserAdminController 增加 PATCH endpoint**

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)")
@PatchMapping("/{userId}/status")
public ApiResponse<Void> updateStatus(@PathVariable Long userId,
                                      @Valid @RequestBody UpdateUserStatusRequest request) {
    userAdminApplicationService.updateStatus(userId, new UpdateUserStatusCommand(request.getStatus(), request.getReason()));
    return ApiResponse.success(null);
}
```

- [ ] **Step 3: 在 service 实现 updateStatus 并触发撤销**

```java
public void updateStatus(Long userId, UpdateUserStatusCommand command) {
    boolean updated = userCommandRepository.updateStatus(userId, command.status());
    if (!updated) {
        throw new ResourceNotFoundException("用户不存在: " + userId);
    }
    if ("DISABLED".equals(command.status())) {
        sessionRevocationService.revokeAllActiveSessions(userId, "user_disabled");
    }
    if ("LOCKED".equals(command.status())) {
        sessionRevocationService.revokeAllActiveSessions(userId, "user_locked");
    }
}
```

- [ ] **Step 4: 在 repo + mapper 补 updateStatus SQL**

```java
@Update("UPDATE iam_user SET status = #{status}, updated_at = NOW() WHERE id = #{userId}")
int updateStatus(@Param("userId") Long userId, @Param("status") String status);
```

```java
@Override
public boolean updateStatus(Long userId, String status) {
    return iamUserMapper.updateStatus(userId, status) > 0;
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -pl whut-eval-app -Dtest=UserAdminControllerWebMvcTest,UserAdminApplicationServiceTest,UserAdminControllerSecurityAnnotationTest test`

Expected: PASS

- [ ] **Step 6: 执行回归测试（P0 相关）**

Run: `mvn -pl whut-eval-app -Dtest=AuthControllerWebMvcTest,UserAdminControllerWebMvcTest,UserAdminApplicationServiceTest,UserAdminControllerSecurityAnnotationTest test`

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserAdminController.java \
        whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/UpdateUserStatusRequest.java \
        whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/UpdateUserStatusCommand.java \
        whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java \
        whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/IamUserCommandRepository.java \
        whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamUserCommandRepository.java \
        whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/IamUserMapper.java
git commit -m "feat: add user status update endpoint and revoke sessions"
```

---

## Self-Review

### 1) Spec coverage
- A-19 `/api/auth/logout`：Task 1-2 覆盖（接口、应用服务、测试）。
- A-5 `GET /api/admin/users`：Task 3-4 覆盖。
- A-6 `POST /api/admin/users`：Task 3-4 覆盖。
- A-7 `PATCH /api/admin/users/{userId}/status`：Task 5-6 覆盖。
- 用户状态触发会话撤销（文档中账号状态与会话一致性要求）：Task 6 覆盖。

### 2) Placeholder scan
- 无 `TODO/TBD`。
- 每个代码步骤都给出具体代码块。
- 每个测试步骤给出具体命令与预期结果。

### 3) Type consistency
- `CreateUserRequest` ↔ `CreateUserCommand` ↔ `UserCreatedResponse` 命名一致。
- `UpdateUserStatusRequest` ↔ `UpdateUserStatusCommand` ↔ `UserAdminApplicationService.updateStatus` 命名一致。
- `LogoutService.logoutByAccessTokenId` 在 controller 与 service 实现一致。

---

Plan complete and saved to `docs/superpowers/plans/2026-05-29-a-group-p0-auth-user-admin-gap-closure.md`. Two execution options:

1. Subagent-Driven (recommended) - I dispatch a fresh subagent per task, review between tasks, fast iteration

2. Inline Execution - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
