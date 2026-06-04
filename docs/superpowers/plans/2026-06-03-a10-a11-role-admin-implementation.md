# A-10 / A-11 Role Admin Write APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 A-10/A-11 落地角色模板写接口：创建角色支持 `roleScope` 校验与持久化，更新角色支持快照并发校验并在冲突时返回 409。

**Architecture:** 在现有 `RoleAdminController` 上增量扩展 POST/PATCH 写接口；新增 `RoleAdminApplicationService` 承接业务校验（roleScope、重复编码、快照冲突）；新增角色写仓储接口及 MyBatisPlus 实现复用 `IamRoleMapper`。测试分注解契约、WebMvc 契约、应用层单测、仓储单测四层，按 TDD 红绿循环推进。

**Tech Stack:** Java 21+, Spring Boot 3.3, Spring Security Method Security, MyBatis-Plus, JUnit 5, Mockito, MockMvc, Maven Surefire。

---

## File Structure (before tasks)

- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/RoleAdminController.java`
  - 新增 A-10/A-11 两个 endpoint，保留 A-9 查询逻辑。
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/CreateRoleRequest.java`
  - A-10 请求体。
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/UpdateRoleRequest.java`
  - A-11 请求体（含快照字段）。
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/response/RoleCreatedResponse.java`
  - A-10 成功响应。

- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/CreateRoleCommand.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/UpdateRoleCommand.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/RoleCreatedView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAdminApplicationService.java`
  - A-10/A-11 写业务：字段校验、冲突校验、持久化。

- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/model/IamRoleDetail.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/RoleAdminCommandRepository.java`
  - 角色写仓储抽象。

- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusRoleAdminCommandRepository.java`
  - 写仓储实现，复用 `IamRoleMapper`。

- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminQueryControllerWebMvcTest.java`
  - 增加 A-10/A-11 的 200/400/404/409 契约测试。
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminControllerSecurityAnnotationTest.java`
  - 反射校验写接口 `@PreAuthorize`。
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminApplicationServiceTest.java`
  - 应用层业务规则单测。
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/infra/MybatisPlusRoleAdminCommandRepositoryTest.java`
  - 仓储层写操作单测（Mockito）。

---

### Task 1: A-10 创建角色接口（TDD）

**Files:**
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/CreateRoleRequest.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/response/RoleCreatedResponse.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/CreateRoleCommand.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/RoleCreatedView.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/model/IamRoleDetail.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/RoleAdminCommandRepository.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAdminApplicationService.java`
- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/RoleAdminController.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminQueryControllerWebMvcTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminApplicationServiceTest.java`

- [ ] **Step 1: 先写 WebMvc 红测（A-10 200/409）**

在 `RoleAdminQueryControllerWebMvcTest` 增加：

```java
@Test
void shouldCreateRole() throws Exception {
    given(roleAdminApplicationService.createRole(any()))
            .willReturn(new RoleCreatedView(31L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE"));

    mockMvc.perform(post("/api/admin/roles")
                    .contentType(APPLICATION_JSON)
                    .content("""
                            {"roleCode":"COUNSELOR","roleName":"辅导员","roleScope":"ORG_SUBTREE"}
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.roleId").value(31))
            .andExpect(jsonPath("$.data.roleCode").value("COUNSELOR"));
}

@Test
void shouldReturn409WhenCreateRoleCodeConflict() throws Exception {
    willThrow(new ConflictException("角色编码已存在: COUNSELOR"))
            .given(roleAdminApplicationService)
            .createRole(any());

    mockMvc.perform(post("/api/admin/roles")
                    .contentType(APPLICATION_JSON)
                    .content("""
                            {"roleCode":"COUNSELOR","roleName":"辅导员","roleScope":"ORG_SUBTREE"}
                            """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("BIZ-4090"));
}
```

- [ ] **Step 2: 运行 WebMvc 测试确认失败**

Run:
```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=RoleAdminQueryControllerWebMvcTest test
```
Expected: FAIL（`roleAdminApplicationService` 或 POST 路由尚不存在）。

- [ ] **Step 3: 写最小实现让 WebMvc 通过（A-10）**

新增 `CreateRoleRequest`：

```java
public class CreateRoleRequest {
    @NotBlank
    private String roleCode;
    @NotBlank
    private String roleName;
    @NotBlank
    private String roleScope;
    // getters/setters
}
```

新增 `RoleCreatedResponse`：

```java
public record RoleCreatedResponse(Long roleId, String roleCode, String roleName, String roleScope, String status) {}
```

在 `RoleAdminController` 注入 `RoleAdminApplicationService`，并新增：

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ROLE_MANAGE)")
@PostMapping
public ApiResponse<RoleCreatedResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
    RoleCreatedView view = roleAdminApplicationService.createRole(
            new CreateRoleCommand(request.getRoleCode(), request.getRoleName(), request.getRoleScope())
    );
    return ApiResponse.success(new RoleCreatedResponse(
            view.roleId(), view.roleCode(), view.roleName(), view.roleScope(), view.status()
    ));
}
```

`RoleAdminApplicationService#createRole` 最小逻辑：

```java
if (!"ORG_SUBTREE".equals(normalize(command.roleScope()))) {
    throw new ValidationException("roleScope 仅允许 ORG_SUBTREE");
}
if (roleAdminCommandRepository.findByRoleCode(normalize(command.roleCode())).isPresent()) {
    throw new ConflictException("角色编码已存在: " + normalize(command.roleCode()));
}
IamRoleDetail created = roleAdminCommandRepository.create(
        normalize(command.roleCode()),
        normalize(command.roleName()),
        "ORG_SUBTREE",
        "ACTIVE"
);
return new RoleCreatedView(created.id(), created.roleCode(), created.roleName(), created.roleScope(), created.status());
```

- [ ] **Step 4: 应用层红测补齐（roleScope 非法、roleCode 重复）**

新增 `RoleAdminApplicationServiceTest`：

```java
@Test
void shouldRejectIllegalRoleScopeOnCreate() {
    assertThatThrownBy(() -> service.createRole(new CreateRoleCommand("COUNSELOR", "辅导员", "ALL")))
            .isInstanceOf(ValidationException.class)
            .hasMessage("roleScope 仅允许 ORG_SUBTREE");
}

@Test
void shouldRejectDuplicateRoleCodeOnCreate() {
    given(roleAdminCommandRepository.findByRoleCode("COUNSELOR"))
            .willReturn(Optional.of(new IamRoleDetail(21L, "COUNSELOR", "旧辅导员", "ORG_SUBTREE", "ACTIVE")));

    assertThatThrownBy(() -> service.createRole(new CreateRoleCommand("COUNSELOR", "辅导员", "ORG_SUBTREE")))
            .isInstanceOf(ConflictException.class)
            .hasMessage("角色编码已存在: COUNSELOR");
}
```

- [ ] **Step 5: 运行 A-10 相关测试确认转绿**

Run:
```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=RoleAdminQueryControllerWebMvcTest,RoleAdminApplicationServiceTest test
```
Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add \
  whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/RoleAdminController.java \
  whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/CreateRoleRequest.java \
  whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/response/RoleCreatedResponse.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/CreateRoleCommand.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/RoleCreatedView.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAdminApplicationService.java \
  whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/model/IamRoleDetail.java \
  whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/RoleAdminCommandRepository.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminQueryControllerWebMvcTest.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminApplicationServiceTest.java

git commit -m "feat(iam): add role create api with roleScope validation"
```

---

### Task 2: A-11 更新角色接口 + 快照并发冲突（TDD）

**Files:**
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/UpdateRoleRequest.java`
- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/RoleAdminController.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/UpdateRoleCommand.java`
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAdminApplicationService.java`
- Modify: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/RoleAdminCommandRepository.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminQueryControllerWebMvcTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminApplicationServiceTest.java`

- [ ] **Step 1: 先写 WebMvc 红测（A-11 200/404/409）**

在 `RoleAdminQueryControllerWebMvcTest` 增加：

```java
@Test
void shouldUpdateRoleWithSnapshot() throws Exception {
    mockMvc.perform(patch("/api/admin/roles/21")
                    .contentType(APPLICATION_JSON)
                    .content("""
                            {
                              "roleName":"辅导员(新)",
                              "roleScope":"ORG_SUBTREE",
                              "status":"ACTIVE",
                              "snapshotRoleName":"辅导员",
                              "snapshotRoleScope":"ORG_SUBTREE",
                              "snapshotStatus":"ACTIVE"
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
}

@Test
void shouldReturn404WhenUpdateRoleNotFound() throws Exception {
    willThrow(new ResourceNotFoundException("角色不存在: 21"))
            .given(roleAdminApplicationService)
            .updateRole(anyLong(), any());

    mockMvc.perform(patch("/api/admin/roles/21")
                    .contentType(APPLICATION_JSON)
                    .content("""
                            {
                              "roleName":"辅导员(新)",
                              "roleScope":"ORG_SUBTREE",
                              "status":"ACTIVE",
                              "snapshotRoleName":"辅导员",
                              "snapshotRoleScope":"ORG_SUBTREE",
                              "snapshotStatus":"ACTIVE"
                            }
                            """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RES-4040"));
}

@Test
void shouldReturn409WhenUpdateRoleSnapshotConflict() throws Exception {
    willThrow(new ConflictException("角色模板已被更新，请刷新后重试"))
            .given(roleAdminApplicationService)
            .updateRole(anyLong(), any());

    mockMvc.perform(patch("/api/admin/roles/21")
                    .contentType(APPLICATION_JSON)
                    .content("""
                            {
                              "roleName":"辅导员(新)",
                              "roleScope":"ORG_SUBTREE",
                              "status":"ACTIVE",
                              "snapshotRoleName":"辅导员",
                              "snapshotRoleScope":"ORG_SUBTREE",
                              "snapshotStatus":"ACTIVE"
                            }
                            """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("BIZ-4090"));
}
```

- [ ] **Step 2: 运行 WebMvc 测试确认失败**

Run:
```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=RoleAdminQueryControllerWebMvcTest test
```
Expected: FAIL（PATCH 路由或 `updateRole` 尚不存在）。

- [ ] **Step 3: 写最小实现让 A-11 路由通过**

新增 `UpdateRoleRequest`：

```java
public class UpdateRoleRequest {
    @NotBlank private String roleName;
    @NotBlank private String roleScope;
    @NotBlank private String status;
    @NotBlank private String snapshotRoleName;
    @NotBlank private String snapshotRoleScope;
    @NotBlank private String snapshotStatus;
    // getters/setters
}
```

新增 `UpdateRoleCommand` 并在 `RoleAdminController` 增加：

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ROLE_MANAGE)")
@PatchMapping("/{roleId}")
public ApiResponse<Void> updateRole(@PathVariable Long roleId,
                                    @Valid @RequestBody UpdateRoleRequest request) {
    roleAdminApplicationService.updateRole(roleId, new UpdateRoleCommand(
            request.getRoleName(), request.getRoleScope(), request.getStatus(),
            request.getSnapshotRoleName(), request.getSnapshotRoleScope(), request.getSnapshotStatus()
    ));
    return ApiResponse.success(null);
}
```

`RoleAdminApplicationService#updateRole` 最小逻辑：

```java
IamRoleDetail current = roleAdminCommandRepository.findById(roleId)
        .orElseThrow(() -> new ResourceNotFoundException("角色不存在: " + roleId));

if (!equalsNormalized(current.roleName(), command.snapshotRoleName())
        || !equalsNormalized(current.roleScope(), command.snapshotRoleScope())
        || !equalsNormalized(current.status(), command.snapshotStatus())) {
    throw new ConflictException("角色模板已被更新，请刷新后重试");
}

validateRoleScope(command.roleScope());
validateStatus(command.status());

roleAdminCommandRepository.update(roleId,
        normalize(command.roleName()),
        normalize(command.roleScope()),
        normalize(command.status()));
```

- [ ] **Step 4: 补应用层红测（404/409/状态非法）**

在 `RoleAdminApplicationServiceTest` 增加：

```java
@Test
void shouldReturn404WhenUpdateRoleNotFound() {
    given(roleAdminCommandRepository.findById(21L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.updateRole(21L, updateCommand()))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("角色不存在: 21");
}

@Test
void shouldRejectUpdateWhenSnapshotConflict() {
    given(roleAdminCommandRepository.findById(21L))
            .willReturn(Optional.of(new IamRoleDetail(21L, "COUNSELOR", "辅导员-已变更", "ORG_SUBTREE", "ACTIVE")));

    assertThatThrownBy(() -> service.updateRole(21L, updateCommand()))
            .isInstanceOf(ConflictException.class)
            .hasMessage("角色模板已被更新，请刷新后重试");
}

@Test
void shouldRejectIllegalStatusOnUpdate() {
    given(roleAdminCommandRepository.findById(21L))
            .willReturn(Optional.of(new IamRoleDetail(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE")));

    assertThatThrownBy(() -> service.updateRole(21L,
            new UpdateRoleCommand("辅导员(新)", "ORG_SUBTREE", "LOCKED", "辅导员", "ORG_SUBTREE", "ACTIVE")))
            .isInstanceOf(ValidationException.class)
            .hasMessage("status 仅允许 ACTIVE 或 DISABLED");
}
```

- [ ] **Step 5: 运行 A-11 相关测试确认转绿**

Run:
```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=RoleAdminQueryControllerWebMvcTest,RoleAdminApplicationServiceTest test
```
Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add \
  whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/UpdateRoleRequest.java \
  whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/RoleAdminController.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/UpdateRoleCommand.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAdminApplicationService.java \
  whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/RoleAdminCommandRepository.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminQueryControllerWebMvcTest.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminApplicationServiceTest.java

git commit -m "feat(iam): add role update api with snapshot conflict check"
```

---

### Task 3: 角色写仓储实现与回归

**Files:**
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusRoleAdminCommandRepository.java`
- Modify: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/RoleAdminCommandRepository.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/infra/MybatisPlusRoleAdminCommandRepositoryTest.java`

- [ ] **Step 1: 先写仓储红测（创建/更新）**

新增 `MybatisPlusRoleAdminCommandRepositoryTest`：

```java
@Test
void shouldCreateRole() {
    given(iamRoleMapper.insert(any(IamRoleDO.class))).willReturn(1);

    IamRoleDetail created = repository.create("COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE");

    assertThat(created.roleCode()).isEqualTo("COUNSELOR");
    assertThat(created.roleScope()).isEqualTo("ORG_SUBTREE");
    then(iamRoleMapper).should().insert(any(IamRoleDO.class));
}

@Test
void shouldUpdateRole() {
    given(iamRoleMapper.updateById(any(IamRoleDO.class))).willReturn(1);

    repository.update(21L, "辅导员(新)", "ORG_SUBTREE", "ACTIVE");

    then(iamRoleMapper).should().updateById(any(IamRoleDO.class));
}
```

- [ ] **Step 2: 运行仓储测试确认失败**

Run:
```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=MybatisPlusRoleAdminCommandRepositoryTest test
```
Expected: FAIL（仓储实现不存在）。

- [ ] **Step 3: 写最小仓储实现**

在 `MybatisPlusRoleAdminCommandRepository` 实现：

```java
@Override
public Optional<IamRoleDetail> findById(Long roleId) {
    IamRoleDO item = iamRoleMapper.selectById(roleId);
    return Optional.ofNullable(item).map(this::toDetail);
}

@Override
public Optional<IamRoleDetail> findByRoleCode(String roleCode) {
    IamRoleDO item = iamRoleMapper.selectOne(new LambdaQueryWrapper<IamRoleDO>()
            .eq(IamRoleDO::getRoleCode, roleCode)
            .last("limit 1"));
    return Optional.ofNullable(item).map(this::toDetail);
}

@Override
public IamRoleDetail create(String roleCode, String roleName, String roleScope, String status) {
    IamRoleDO item = new IamRoleDO();
    item.setRoleCode(roleCode);
    item.setRoleName(roleName);
    item.setRoleScope(roleScope);
    item.setStatus(status);
    item.setCreatedAt(LocalDateTime.now());
    iamRoleMapper.insert(item);
    return toDetail(item);
}

@Override
public void update(Long roleId, String roleName, String roleScope, String status) {
    IamRoleDO item = new IamRoleDO();
    item.setId(roleId);
    item.setRoleName(roleName);
    item.setRoleScope(roleScope);
    item.setStatus(status);
    iamRoleMapper.updateById(item);
}
```

- [ ] **Step 4: 运行仓储测试确认通过**

Run:
```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=MybatisPlusRoleAdminCommandRepositoryTest test
```
Expected: PASS。

- [ ] **Step 5: 运行 A-10/A-11 定向回归**

Run:
```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=RoleAdminControllerSecurityAnnotationTest,RoleAdminQueryControllerWebMvcTest,RoleAdminApplicationServiceTest,MybatisPlusRoleAdminCommandRepositoryTest test
```
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git add \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusRoleAdminCommandRepository.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/infra/MybatisPlusRoleAdminCommandRepositoryTest.java

git commit -m "feat(iam): persist role create and update commands"
```

---

### Task 4: 接力文档（clear 前交接）

**Files:**
- Create: `docs/superpowers/plans/2026-06-03-a10-a11-handoff.md`

- [ ] **Step 1: 写交接文档（已完成/未完成/测试命令）**

文档模板：

```markdown
# A-10/A-11 Handoff

## Done
- [x] A-10 ...
- [x] A-11 ...

## Commits
- <sha> <message>

## Verification
- mvn ... -Dtest=...

## Next
- A-12 replaceAll ...
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/plans/2026-06-03-a10-a11-handoff.md

git commit -m "docs(plan): add A-10 A-11 handoff summary"
```

---

## 测试计划总览

1. Controller 注解契约：`RoleAdminControllerSecurityAnnotationTest`
2. WebMvc 契约：`RoleAdminQueryControllerWebMvcTest`
3. 应用层规则：`RoleAdminApplicationServiceTest`
4. 仓储写操作：`MybatisPlusRoleAdminCommandRepositoryTest`
5. 回归：上述 4 类测试组合执行

---

## 回滚策略

- 回滚顺序：Task 4 -> Task 3 -> Task 2 -> Task 1
- 若线上出现误拦截，可先回滚 Task 2（快照并发校验）
- 若仅文档需调整，可单独回滚 Task 4

---

## 验收清单

- [ ] `POST /api/admin/roles` 可创建角色并返回 role 快照
- [ ] `roleScope` 非法返回 400
- [ ] 重复 `roleCode` 返回 409
- [ ] `PATCH /api/admin/roles/{roleId}` 支持快照并发校验
- [ ] 快照冲突返回 409
- [ ] 角色不存在返回 404
- [ ] A-10/A-11 定向测试全通过

---

## Spec Coverage Self-Review

- A-10 创建角色 + roleScope 校验：Task 1 + Task 3
- A-11 快照并发校验返回 409：Task 2
- 仅后端接口+测试范围：Task 1~3（不含前端）
- clear 前可接续材料：Task 4
