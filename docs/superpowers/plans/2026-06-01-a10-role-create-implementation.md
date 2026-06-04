# A-10 Create Role API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 `POST /api/admin/roles`（A-10），支持角色创建主流程与重复角色编码冲突场景，并通过对应测试。

**Architecture:** 在现有 `RoleAdminController` 上新增 POST 路由，Controller 仅做 DTO 与 Command/View 映射；新增应用服务处理创建规则（必填 + 唯一性），复用 `ConflictException/ValidationException` 与全局异常映射。持久化侧新增最小写仓储接口与 MyBatisPlus 实现，避免影响现有查询链路。

**Tech Stack:** Spring Boot WebMvc, Jakarta Validation, MyBatis-Plus, JUnit5, Mockito, MockMvc, Maven

---

## File Structure (Planned)

- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/RoleAdminController.java`
  - 责任：新增 `POST /api/admin/roles` 路由，调用应用服务并返回统一响应。
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/CreateRoleRequest.java`
  - 责任：入参校验（`roleCode`/`roleName` 必填）。
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/response/RoleCreatedResponse.java`
  - 责任：创建结果响应体。
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/CreateRoleCommand.java`
  - 责任：应用层创建命令。
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/RoleCreatedView.java`
  - 责任：应用层创建结果视图。
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAdminApplicationService.java`
  - 责任：创建角色业务规则（校验+唯一性）与写入编排。
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/IamRoleCommandRepository.java`
  - 责任：领域写仓储契约。
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamRoleCommandRepository.java`
  - 责任：插入 `iam_role` 并返回领域模型。
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminQueryControllerWebMvcTest.java`
  - 责任：新增 A-10 WebMvc 红绿用例。
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminApplicationServiceTest.java`
  - 责任：应用服务规则单测（成功 + 冲突 + 必填）。
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/infra/MybatisPlusIamRoleCommandRepositoryTest.java`
  - 责任：持久化写仓储单测（插入映射）。

---

### Task 1: WebMvc 红灯——先定义 A-10 接口行为

**Files:**
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminQueryControllerWebMvcTest.java`

- [ ] **Step 1: 写失败测试（创建成功）**

```java
@Test
void shouldCreateRole() throws Exception {
    given(roleAdminApplicationService.createRole(any(CreateRoleCommand.class)))
            .willReturn(new RoleCreatedView(31L, "COUNSELOR_NEW", "新辅导员", "ACTIVE"));

    mockMvc.perform(post("/api/admin/roles")
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CreateRolePayload(
                            "COUNSELOR_NEW",
                            "新辅导员"
                    ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.roleId").value(31))
            .andExpect(jsonPath("$.data.roleCode").value("COUNSELOR_NEW"))
            .andExpect(jsonPath("$.data.roleName").value("新辅导员"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
}
```

- [ ] **Step 2: 写失败测试（重复 roleCode 冲突）**

```java
@Test
void shouldReturn409WhenRoleCodeAlreadyExists() throws Exception {
    given(roleAdminApplicationService.createRole(any(CreateRoleCommand.class)))
            .willThrow(new ConflictException("角色编码已存在: COUNSELOR"));

    mockMvc.perform(post("/api/admin/roles")
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CreateRolePayload(
                            "COUNSELOR",
                            "辅导员"
                    ))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("BIZ-4090"))
            .andExpect(jsonPath("$.message").value("角色编码已存在: COUNSELOR"));
}
```

- [ ] **Step 3: 单跑测试确认 RED**

Run:
```bash
mvn -pl whut-eval-app -Dtest=RoleAdminQueryControllerWebMvcTest#shouldCreateRole,RoleAdminQueryControllerWebMvcTest#shouldReturn409WhenRoleCodeAlreadyExists test
```

Expected:
- FAIL，且失败点为 `RoleAdminController` 还没有 POST 路由/缺少创建依赖（不是断言或拼写错误）。

- [ ] **Step 4: 提交（仅测试变更）**

```bash
git add whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminQueryControllerWebMvcTest.java
git commit -m "test: add failing webmvc tests for admin role creation"
```

---

### Task 2: 绿灯——最小 Controller + DTO + Command/View 接口契约

**Files:**
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/CreateRoleRequest.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/response/RoleCreatedResponse.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/CreateRoleCommand.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/RoleCreatedView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAdminApplicationService.java`
- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/RoleAdminController.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminQueryControllerWebMvcTest.java`（补 `ObjectMapper`、MockBean、payload record）

- [ ] **Step 1: 创建请求 DTO（含校验）**

```java
public class CreateRoleRequest {

    @NotBlank
    private String roleCode;

    @NotBlank
    private String roleName;

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
}
```

- [ ] **Step 2: 创建 Command/View/Response 与服务骨架**

```java
public record CreateRoleCommand(String roleCode, String roleName) {}

public record RoleCreatedView(Long roleId, String roleCode, String roleName, String status) {}

public class RoleCreatedResponse {
    private final Long roleId;
    private final String roleCode;
    private final String roleName;
    private final String status;
    // constructor + getters
}

@Service
public class RoleAdminApplicationService {
    public RoleCreatedView createRole(CreateRoleCommand command) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
```

- [ ] **Step 3: 在 Controller 新增 POST 路由（最小映射）**

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ROLE_MANAGE)")
@PostMapping
public ApiResponse<RoleCreatedResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
    RoleCreatedView view = roleAdminApplicationService.createRole(
            new CreateRoleCommand(request.getRoleCode(), request.getRoleName())
    );
    return ApiResponse.success(new RoleCreatedResponse(
            view.roleId(),
            view.roleCode(),
            view.roleName(),
            view.status()
    ));
}
```

- [ ] **Step 4: 运行 Task 1 用例确认转绿**

Run:
```bash
mvn -pl whut-eval-app -Dtest=RoleAdminQueryControllerWebMvcTest#shouldCreateRole,RoleAdminQueryControllerWebMvcTest#shouldReturn409WhenRoleCodeAlreadyExists test
```

Expected:
- PASS（如果冲突用例仍报 500，进入 Task 3 完成应用服务异常逻辑）。

- [ ] **Step 5: 提交（接口契约与路由）**

```bash
git add whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/RoleAdminController.java \
        whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/CreateRoleRequest.java \
        whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/response/RoleCreatedResponse.java \
        whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/CreateRoleCommand.java \
        whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/RoleCreatedView.java \
        whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAdminApplicationService.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminQueryControllerWebMvcTest.java
git commit -m "feat: add admin role create endpoint contract"
```

---

### Task 3: 应用服务红绿——唯一性与参数规则

**Files:**
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminApplicationServiceTest.java`
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAdminApplicationService.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/IamRoleCommandRepository.java`

- [ ] **Step 1: 写失败测试（创建成功）**

```java
@Test
void shouldCreateRoleWhenRoleCodeNotExists() {
    given(iamRoleQueryRepository.findByRoleCode("COUNSELOR_NEW")).willReturn(Optional.empty());
    given(iamRoleCommandRepository.createRole("COUNSELOR_NEW", "新辅导员", "ORG_SUBTREE", "ACTIVE"))
            .willReturn(new IamRoleDefinition(31L, "COUNSELOR_NEW", "新辅导员", "ACTIVE"));

    RoleCreatedView view = service.createRole(new CreateRoleCommand("COUNSELOR_NEW", "新辅导员"));

    assertThat(view.roleId()).isEqualTo(31L);
    assertThat(view.roleCode()).isEqualTo("COUNSELOR_NEW");
    assertThat(view.status()).isEqualTo("ACTIVE");
}
```

- [ ] **Step 2: 写失败测试（重复 roleCode 冲突）**

```java
@Test
void shouldRejectCreateRoleWhenRoleCodeAlreadyExists() {
    given(iamRoleQueryRepository.findByRoleCode("COUNSELOR"))
            .willReturn(Optional.of(new IamRoleDefinition(21L, "COUNSELOR", "辅导员", "ACTIVE")));

    assertThatThrownBy(() -> service.createRole(new CreateRoleCommand("COUNSELOR", "辅导员")))
            .isInstanceOf(ConflictException.class)
            .hasMessage("角色编码已存在: COUNSELOR");
}
```

- [ ] **Step 3: 写失败测试（必填校验）**

```java
@Test
void shouldRejectCreateRoleWhenRoleCodeBlank() {
    assertThatThrownBy(() -> service.createRole(new CreateRoleCommand("   ", "辅导员")))
            .isInstanceOf(ValidationException.class)
            .hasMessage("roleCode 不能为空");
}
```

- [ ] **Step 4: 单跑服务测试确认 RED**

Run:
```bash
mvn -pl whut-eval-app -Dtest=RoleAdminApplicationServiceTest test
```

Expected:
- FAIL，失败原因为 `RoleAdminApplicationService#createRole` 未实现或依赖缺失。

- [ ] **Step 5: 最小实现应用服务 + 仓储接口**

```java
public interface IamRoleCommandRepository {
    IamRoleDefinition createRole(String roleCode, String roleName, String roleScope, String status);
}

@Service
public class RoleAdminApplicationService {

    private final IamRoleQueryRepository iamRoleQueryRepository;
    private final IamRoleCommandRepository iamRoleCommandRepository;

    public RoleAdminApplicationService(IamRoleQueryRepository iamRoleQueryRepository,
                                       IamRoleCommandRepository iamRoleCommandRepository) {
        this.iamRoleQueryRepository = iamRoleQueryRepository;
        this.iamRoleCommandRepository = iamRoleCommandRepository;
    }

    @Transactional
    public RoleCreatedView createRole(CreateRoleCommand command) {
        String roleCode = normalize(command.roleCode());
        String roleName = normalize(command.roleName());
        if (roleCode == null) {
            throw new ValidationException("roleCode 不能为空");
        }
        if (roleName == null) {
            throw new ValidationException("roleName 不能为空");
        }
        iamRoleQueryRepository.findByRoleCode(roleCode).ifPresent(it -> {
            throw new ConflictException("角色编码已存在: " + roleCode);
        });

        IamRoleDefinition created = iamRoleCommandRepository.createRole(roleCode, roleName, "ORG_SUBTREE", "ACTIVE");
        return new RoleCreatedView(created.roleId(), created.roleCode(), created.roleName(), created.status());
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
```

- [ ] **Step 6: 回跑服务测试确认 GREEN**

Run:
```bash
mvn -pl whut-eval-app -Dtest=RoleAdminApplicationServiceTest test
```

Expected:
- PASS。

- [ ] **Step 7: 提交（应用服务规则）**

```bash
git add whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminApplicationServiceTest.java \
        whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAdminApplicationService.java \
        whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/IamRoleCommandRepository.java
git commit -m "feat: add role create application service with conflict validation"
```

---

### Task 4: 基础设施红绿——写仓储实现

**Files:**
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamRoleCommandRepository.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/infra/MybatisPlusIamRoleCommandRepositoryTest.java`

- [ ] **Step 1: 写失败测试（插入映射）**

```java
@Test
void shouldCreateRole() {
    given(iamRoleMapper.insert(any(IamRoleDO.class))).willAnswer(invocation -> {
        IamRoleDO entity = invocation.getArgument(0);
        entity.setId(31L);
        return 1;
    });

    IamRoleDefinition result = repository.createRole("COUNSELOR_NEW", "新辅导员", "ORG_SUBTREE", "ACTIVE");

    assertThat(result.roleId()).isEqualTo(31L);
    assertThat(result.roleCode()).isEqualTo("COUNSELOR_NEW");
    assertThat(result.roleName()).isEqualTo("新辅导员");
    assertThat(result.status()).isEqualTo("ACTIVE");
}
```

- [ ] **Step 2: 单跑仓储测试确认 RED**

Run:
```bash
mvn -pl whut-eval-app -Dtest=MybatisPlusIamRoleCommandRepositoryTest test
```

Expected:
- FAIL，失败原因为 `MybatisPlusIamRoleCommandRepository` 尚未实现。

- [ ] **Step 3: 最小实现仓储**

```java
@Repository
public class MybatisPlusIamRoleCommandRepository implements IamRoleCommandRepository {

    private final IamRoleMapper iamRoleMapper;

    public MybatisPlusIamRoleCommandRepository(IamRoleMapper iamRoleMapper) {
        this.iamRoleMapper = iamRoleMapper;
    }

    @Override
    public IamRoleDefinition createRole(String roleCode, String roleName, String roleScope, String status) {
        IamRoleDO entity = new IamRoleDO();
        entity.setRoleCode(roleCode);
        entity.setRoleName(roleName);
        entity.setRoleScope(roleScope);
        entity.setStatus(status);
        entity.setCreatedAt(LocalDateTime.now());
        iamRoleMapper.insert(entity);
        return new IamRoleDefinition(entity.getId(), entity.getRoleCode(), entity.getRoleName(), entity.getStatus());
    }
}
```

- [ ] **Step 4: 回跑仓储测试确认 GREEN**

Run:
```bash
mvn -pl whut-eval-app -Dtest=MybatisPlusIamRoleCommandRepositoryTest test
```

Expected:
- PASS。

- [ ] **Step 5: 提交（写仓储）**

```bash
git add whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamRoleCommandRepository.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/infra/MybatisPlusIamRoleCommandRepositoryTest.java
git commit -m "feat: implement mybatis role command repository for role creation"
```

---

### Task 5: A-10 回归与小步重构

**Files:**
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminQueryControllerWebMvcTest.java`（必要时整理重复 setup）
- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/RoleAdminController.java`（必要时提取局部映射方法）

- [ ] **Step 1: 运行 A-10 相关测试集合**

Run:
```bash
mvn -pl whut-eval-app -Dtest=RoleAdminQueryControllerWebMvcTest,RoleAdminApplicationServiceTest,MybatisPlusIamRoleCommandRepositoryTest test
```

Expected:
- 全绿，且无新增 warning/error。

- [ ] **Step 2: 运行角色域基线回归**

Run:
```bash
mvn -pl whut-eval-app -Dtest=DefaultRoleAdminQueryApplicationServiceTest,MybatisPlusIamRoleQueryRepositoryTest test
```

Expected:
- 全绿，确认未破坏既有角色查询路径。

- [ ] **Step 3: 小步重构（仅当有重复）**

```java
private RoleCreatedResponse toCreatedResponse(RoleCreatedView view) {
    return new RoleCreatedResponse(view.roleId(), view.roleCode(), view.roleName(), view.status());
}
```

- [ ] **Step 4: 最终回归**

Run:
```bash
mvn -pl whut-eval-app -Dtest=RoleAdminQueryControllerWebMvcTest,RoleAdminApplicationServiceTest,MybatisPlusIamRoleCommandRepositoryTest,DefaultRoleAdminQueryApplicationServiceTest,MybatisPlusIamRoleQueryRepositoryTest test
```

Expected:
- 全绿。

- [ ] **Step 5: 提交（回归与重构）**

```bash
git add whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminQueryControllerWebMvcTest.java \
        whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/RoleAdminController.java
git commit -m "refactor: finalize a10 role create flow with passing regression suite"
```

---

## Acceptance Checklist (A-10)

- [ ] `POST /api/admin/roles` 路由存在且受 `ROLE_MANAGE` 权限保护。
- [ ] 成功创建返回 `roleId/roleCode/roleName/status`。
- [ ] 重复 `roleCode` 返回 409（`BIZ-4090`）语义。
- [ ] 至少包含主流程 + 冲突场景自动化测试。
- [ ] A-10 相关测试与角色查询回归测试全部通过。

## Spec Coverage Self-Review

- 需求“最小改动接入 RoleAdminController”：由 Task 2 覆盖。
- 需求“唯一性冲突语义”：由 Task 1（WebMvc）+ Task 3（Service）覆盖。
- 需求“TDD 红绿重构”：每个 Task 均包含 RED→GREEN→验证→commit。
- 需求“不改全局异常体系”：计划只复用 `ConflictException/ValidationException`，不触碰 `GlobalExceptionHandler`。

无 TBD/TODO 占位，无跨 A-10 范围扩展任务。