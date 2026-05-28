# A Group Phase 1 Role Assignment Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐 A 组第一阶段的 `A-13 分页查询角色分配` 和 `A-16 撤销角色分配`，形成角色分配管理最小闭环。

**Architecture:** 继续沿用现有 `interfaces -> application -> domain repository -> infra repository` 分层，不新增第二套管理边界。`A-13` 通过新增分页查询投影模型补齐 `userNo/userName` 列表字段，`A-16` 通过显式撤销语义把分配状态落为 `INACTIVE`，并复用已有 operator context 与审计扩展点。

**Tech Stack:** Spring Boot 3, Spring MVC, Spring Security `@PreAuthorize`, MyBatis-Plus, H2 integration test, JUnit 5, Mockito

---

### Task 1: 补齐接口契约与 Controller 失败测试

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/RoleAssignmentAdminPageQuery.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/RoleAssignmentAdminPageItemView.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/response/RoleAssignmentPageItemResponse.java`
- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/IamRoleAssignmentAdminController.java`
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAssignmentAdminApplicationService.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/IamRoleAssignmentAdminControllerWebMvcTest.java`

- [ ] **Step 1: 先写 `A-13` 的 WebMvc 失败测试**

```java
@Test
void shouldPageRoleAssignments() throws Exception {
    given(roleAssignmentAdminApplicationService.pageAssignments(any()))
            .willReturn(new PageResult<>(
                    1,
                    List.of(new RoleAssignmentAdminPageItemView(
                            70021L,
                            1010L,
                            "2024305001",
                            "王老师",
                            "COUNSELOR",
                            "辅导员",
                            2002L,
                            "计算机与人工智能学院",
                            "ACTIVE",
                            "2026-05-18T00:00:00",
                            "2027-07-01T00:00:00"
                    ))
            ));

    mockMvc.perform(get("/api/admin/role-assignments")
                    .param("pageNo", "1")
                    .param("pageSize", "20")
                    .param("userId", "1010")
                    .param("roleCode", "COUNSELOR")
                    .param("status", "ACTIVE")
                    .param("orgUnitId", "2002"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].userNo").value("2024305001"))
            .andExpect(jsonPath("$.data.records[0].roleCode").value("COUNSELOR"));
}
```

- [ ] **Step 2: 运行单测，确认 `pageAssignments()` 和 GET 路由尚未实现**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=IamRoleAssignmentAdminControllerWebMvcTest#shouldPageRoleAssignments -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，报 `cannot find symbol: method pageAssignments(...)` 或 controller 中缺少 `GET /api/admin/role-assignments`

- [ ] **Step 3: 再写 `A-16` 的 WebMvc 失败测试**

```java
@Test
void shouldRevokeRoleAssignment() throws Exception {
    given(roleAssignmentAdminApplicationService.revokeAssignment(70021L))
            .willReturn();

    mockMvc.perform(delete("/api/admin/role-assignments/70021"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").doesNotExist());
}
```

- [ ] **Step 4: 运行单测，确认 DELETE 路由与 service 方法都还不存在**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=IamRoleAssignmentAdminControllerWebMvcTest#shouldRevokeRoleAssignment -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，报 `cannot find symbol: method revokeAssignment(Long)` 或 controller 缺少 `@DeleteMapping`

- [ ] **Step 5: 写最小接口契约，让 Web 层能编译**

```java
public interface RoleAssignmentAdminApplicationService {

    RoleAssignmentAdminView createAssignment(CreateRoleAssignmentCommand command);

    RoleAssignmentAdminView updateAssignment(Long assignmentId, UpdateRoleAssignmentCommand command);

    PageResult<RoleAssignmentAdminPageItemView> pageAssignments(RoleAssignmentAdminPageQuery query);

    void revokeAssignment(Long assignmentId);
}
```

```java
public record RoleAssignmentAdminPageQuery(
        long pageNo,
        long pageSize,
        Long userId,
        String roleCode,
        String status,
        Long orgUnitId
) {
}
```

```java
public record RoleAssignmentAdminPageItemView(
        Long assignmentId,
        Long userId,
        String userNo,
        String userName,
        String roleCode,
        String roleName,
        Long orgUnitId,
        String orgUnitName,
        String status,
        String effectiveFrom,
        String effectiveTo
) {
}
```

- [ ] **Step 6: 在 controller 加最小路由与响应映射**

```java
@GetMapping
public ApiResponse<PageResult<RoleAssignmentPageItemResponse>> pageRoleAssignments(
        @RequestParam(defaultValue = "1") long pageNo,
        @RequestParam(defaultValue = "20") long pageSize,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String roleCode,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long orgUnitId) {
    PageResult<RoleAssignmentAdminPageItemView> page = roleAssignmentAdminApplicationService.pageAssignments(
            new RoleAssignmentAdminPageQuery(pageNo, pageSize, userId, roleCode, status, orgUnitId)
    );
    return ApiResponse.success(new PageResult<>(
            page.total(),
            page.records().stream().map(this::toRoleAssignmentPageItemResponse).toList()
    ));
}

@DeleteMapping("/{assignmentId}")
public ApiResponse<Void> revokeRoleAssignment(@PathVariable Long assignmentId) {
    roleAssignmentAdminApplicationService.revokeAssignment(assignmentId);
    return ApiResponse.success(null);
}
```

```java
private RoleAssignmentPageItemResponse toRoleAssignmentPageItemResponse(RoleAssignmentAdminPageItemView view) {
    return new RoleAssignmentPageItemResponse(
            view.assignmentId(),
            view.userId(),
            view.userNo(),
            view.userName(),
            view.roleCode(),
            view.roleName(),
            view.orgUnitId(),
            view.orgUnitName(),
            view.status(),
            view.effectiveFrom(),
            view.effectiveTo()
    );
}
```

- [ ] **Step 7: 回跑 controller 测试，确认 Web 层通过、实现层开始红**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=IamRoleAssignmentAdminControllerWebMvcTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `shouldPageRoleAssignments` / `shouldRevokeRoleAssignment` 从编译错误转为 service 未实现或 NPE 风险，说明 Web 契约已就位

### Task 2: 补齐 Application Service 的失败测试与最小实现

**Files:**
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/DefaultRoleAssignmentAdminApplicationServiceTest.java`
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/DefaultRoleAssignmentAdminApplicationService.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/query/RoleAssignmentAdminPageQuery.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/model/IamRoleAssignmentAdminRecord.java`

- [ ] **Step 1: 先写 `A-13` 的 application service 失败测试**

```java
@Test
void shouldPageRoleAssignments() {
    given(roleAssignmentAdminRepository.pageAssignments(any(RoleAssignmentAdminPageQuery.class)))
            .willReturn(new PageResult<>(
                    1,
                    List.of(new IamRoleAssignmentAdminRecord(
                            70021L,
                            1010L,
                            "2024305001",
                            "王老师",
                            "COUNSELOR",
                            "辅导员",
                            2002L,
                            "计算机与人工智能学院",
                            "ACTIVE",
                            "2026-05-18T00:00:00",
                            "2027-07-01T00:00:00"
                    ))
            ));

    PageResult<RoleAssignmentAdminPageItemView> result = service.pageAssignments(
            new edu.whut.eval.application.iam.query.RoleAssignmentAdminPageQuery(1, 20, 1010L, "COUNSELOR", "ACTIVE", 2002L)
    );

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.records().getFirst().userNo()).isEqualTo("2024305001");
}
```

- [ ] **Step 2: 运行单测，确认 service 和 repository 契约还没补齐**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=DefaultRoleAssignmentAdminApplicationServiceTest#shouldPageRoleAssignments -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，报 `pageAssignments(...)` 不存在

- [ ] **Step 3: 再写 `A-16` 的 application service 失败测试**

```java
@Test
void shouldRevokeActiveAssignment() {
    IamRoleAssignmentDetail existing = new IamRoleAssignmentDetail(
            70021L, 1010L, "COUNSELOR", "辅导员", 2002L, "计算机与人工智能学院",
            "ACTIVE", "2026-05-18T00:00:00", "2027-07-01T00:00:00", "MANUAL", null
    );
    given(roleAssignmentAdminRepository.findDetailById(70021L)).willReturn(Optional.of(existing));
    given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(
            new UserAuthorizationContext(9001L, "A0001", "系统管理员", "ADMIN", Set.of("SUPER_ADMIN"), Set.of("assignment.manage"), List.of())
    );

    service.revokeAssignment(70021L);

    verify(roleAssignmentAdminRepository).revoke(70021L);
}
```

- [ ] **Step 4: 运行单测，确认撤销方法与仓储契约还不存在**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=DefaultRoleAssignmentAdminApplicationServiceTest#shouldRevokeActiveAssignment -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，报 `revokeAssignment(...)` / `revoke(...)` 不存在

- [ ] **Step 5: 把分页查询对象下沉到 domain，避免 domain repo 依赖 application**

```java
public record RoleAssignmentAdminPageQuery(
        long pageNo,
        long pageSize,
        Long userId,
        String roleCode,
        String status,
        Long orgUnitId
) {
}
```

```java
public record IamRoleAssignmentAdminRecord(
        Long assignmentId,
        Long userId,
        String userNo,
        String userName,
        String roleCode,
        String roleName,
        Long orgUnitId,
        String orgUnitName,
        String status,
        String effectiveFrom,
        String effectiveTo
) {
}
```

- [ ] **Step 6: 在默认 service 中补最小实现**

```java
@Override
@Transactional(readOnly = true)
public PageResult<RoleAssignmentAdminPageItemView> pageAssignments(
        edu.whut.eval.application.iam.query.RoleAssignmentAdminPageQuery query) {
    PageResult<IamRoleAssignmentAdminRecord> page = roleAssignmentAdminRepository.pageAssignments(
            new edu.whut.eval.domain.iam.query.RoleAssignmentAdminPageQuery(
                    query.pageNo(), query.pageSize(), query.userId(), query.roleCode(), query.status(), query.orgUnitId()
            )
    );
    return new PageResult<>(
            page.total(),
            page.records().stream()
                    .map(record -> new RoleAssignmentAdminPageItemView(
                            record.assignmentId(),
                            record.userId(),
                            record.userNo(),
                            record.userName(),
                            record.roleCode(),
                            record.roleName(),
                            record.orgUnitId(),
                            record.orgUnitName(),
                            record.status(),
                            record.effectiveFrom(),
                            record.effectiveTo()
                    ))
                    .toList()
    );
}

@Override
@Transactional
public void revokeAssignment(Long assignmentId) {
    UserAuthorizationContext operator = userAuthorizationContextAssembler.requiredAuthorizationContext();
    IamRoleAssignmentDetail existing = roleAssignmentAdminRepository.findDetailById(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException("角色分配不存在: " + assignmentId));
    if (!"ACTIVE".equals(existing.status())) {
        throw new ConflictException("仅 ACTIVE 状态允许撤销");
    }
    roleAssignmentAdminRepository.revoke(assignmentId);
    iamAdminAuditRecorder.recordRoleAssignmentUpdated(
            operator.getUserId(),
            existing,
            new IamRoleAssignmentDetail(
                    existing.assignmentId(), existing.userId(), existing.roleCode(), existing.roleName(),
                    existing.orgUnitId(), existing.orgUnitName(), "INACTIVE",
                    existing.effectiveFrom(), existing.effectiveTo(), existing.sourceType(), existing.updatedAt()
            )
    );
}
```

- [ ] **Step 7: 回跑 application service 测试，确认逻辑层通过**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=DefaultRoleAssignmentAdminApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS，至少包含新增的 `shouldPageRoleAssignments` 和 `shouldRevokeActiveAssignment`

### Task 3: 补齐 Repository 失败测试与 MyBatis 实现

**Files:**
- Modify: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/RoleAssignmentAdminRepository.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/infra/MybatisPlusRoleAssignmentAdminRepositoryTest.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusRoleAssignmentAdminRepository.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/IamUserRoleAssignmentMapper.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/RoleAssignmentAdminPageRow.java`

- [ ] **Step 1: 先写 repository unit test，覆盖分页查询和撤销**

```java
@Test
void shouldPageRoleAssignments() {
    given(assignmentMapper.selectAdminPageRows(any())).willReturn(List.of(
            new RoleAssignmentAdminPageRow(
                    70021L, 1010L, "2024305001", "王老师", "COUNSELOR", "辅导员",
                    2002L, "计算机与人工智能学院", "ACTIVE",
                    "2026-05-18T00:00:00", "2027-07-01T00:00:00"
            )
    ));
    given(assignmentMapper.countAdminPageRows(any())).willReturn(1L);

    PageResult<IamRoleAssignmentAdminRecord> result = repository.pageAssignments(
            new RoleAssignmentAdminPageQuery(1, 20, 1010L, "COUNSELOR", "ACTIVE", 2002L)
    );

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.records().getFirst().userName()).isEqualTo("王老师");
}

@Test
void shouldRevokeRoleAssignmentByUpdatingStatus() {
    IamUserRoleAssignmentDO assignmentDO = new IamUserRoleAssignmentDO();
    assignmentDO.setId(70021L);
    assignmentDO.setStatus("ACTIVE");
    given(assignmentMapper.selectById(70021L)).willReturn(assignmentDO);

    repository.revoke(70021L);

    assertThat(assignmentDO.getStatus()).isEqualTo("INACTIVE");
    verify(assignmentMapper).updateById(assignmentDO);
}
```

- [ ] **Step 2: 运行单测，确认 mapper 自定义分页查询与 `revoke()` 还没实现**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=MybatisPlusRoleAssignmentAdminRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，报 `selectAdminPageRows` / `countAdminPageRows` / `revoke` 缺失

- [ ] **Step 3: 先扩 domain repo 契约，再补 mapper row**

```java
public interface RoleAssignmentAdminRepository {

    boolean existsActiveAssignment(Long userId, String roleCode, Long orgUnitId, Long excludeAssignmentId);

    PageResult<IamRoleAssignmentAdminRecord> pageAssignments(RoleAssignmentAdminPageQuery query);

    void revoke(Long assignmentId);

    IamRoleAssignmentDetail create(...);

    Optional<IamRoleAssignmentDetail> findDetailById(Long assignmentId);

    IamRoleAssignmentDetail update(...);
}
```

```java
public record RoleAssignmentAdminPageRow(
        Long assignmentId,
        Long userId,
        String userNo,
        String userName,
        String roleCode,
        String roleName,
        Long orgUnitId,
        String orgUnitName,
        String status,
        String effectiveFrom,
        String effectiveTo
) {
}
```

- [ ] **Step 4: 在 mapper 上补最小分页 SQL**

```java
@Select("""
        <script>
        SELECT a.id AS assignmentId,
               a.user_id AS userId,
               u.user_no AS userNo,
               u.user_name AS userName,
               r.role_code AS roleCode,
               r.role_name AS roleName,
               a.org_unit_id AS orgUnitId,
               ou.unit_name AS orgUnitName,
               a.status AS status,
               CAST(a.effective_from AS CHAR) AS effectiveFrom,
               CAST(a.effective_to AS CHAR) AS effectiveTo
        FROM iam_user_role_assignment a
        JOIN iam_user u ON u.id = a.user_id
        JOIN iam_role r ON r.id = a.role_id
        LEFT JOIN org_unit ou ON ou.id = a.org_unit_id
        <where>
          <if test="query.userId != null">AND a.user_id = #{query.userId}</if>
          <if test="query.roleCode != null and query.roleCode != ''">AND r.role_code = #{query.roleCode}</if>
          <if test="query.status != null and query.status != ''">AND a.status = #{query.status}</if>
          <if test="query.orgUnitId != null">AND a.org_unit_id = #{query.orgUnitId}</if>
        </where>
        ORDER BY a.id ASC
        LIMIT #{limit} OFFSET #{offset}
        </script>
        """)
List<RoleAssignmentAdminPageRow> selectAdminPageRows(@Param("query") RoleAssignmentAdminPageQuery query,
                                                     @Param("offset") long offset,
                                                     @Param("limit") long limit);
```

- [ ] **Step 5: 在 repository 实现中补最小映射与撤销**

```java
@Override
public PageResult<IamRoleAssignmentAdminRecord> pageAssignments(RoleAssignmentAdminPageQuery query) {
    long offset = (query.pageNo() - 1) * query.pageSize();
    List<IamRoleAssignmentAdminRecord> records = assignmentMapper
            .selectAdminPageRows(query, offset, query.pageSize())
            .stream()
            .map(row -> new IamRoleAssignmentAdminRecord(
                    row.assignmentId(), row.userId(), row.userNo(), row.userName(),
                    row.roleCode(), row.roleName(), row.orgUnitId(), row.orgUnitName(),
                    row.status(), row.effectiveFrom(), row.effectiveTo()
            ))
            .toList();
    return new PageResult<>(assignmentMapper.countAdminPageRows(query), records);
}

@Override
public void revoke(Long assignmentId) {
    IamUserRoleAssignmentDO assignmentDO = assignmentMapper.selectById(assignmentId);
    if (assignmentDO == null) {
        return;
    }
    assignmentDO.setStatus("INACTIVE");
    assignmentMapper.updateById(assignmentDO);
}
```

- [ ] **Step 6: 回跑 repository unit test，确认映射和状态更新通过**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=MybatisPlusRoleAssignmentAdminRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS，新增的分页与撤销单测通过

### Task 4: 用 H2 集成测试锁定真实 SQL 行为并做阶段验收

**Files:**
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/MybatisPlusRoleAssignmentAdminRepositoryIntegrationTest.java`
- Verify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/IamRoleAssignmentAdminControllerWebMvcTest.java`
- Verify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/DefaultRoleAssignmentAdminApplicationServiceTest.java`
- Verify: `whut-eval-app/src/test/java/edu/whut/eval/app/infra/MybatisPlusRoleAssignmentAdminRepositoryTest.java`

- [ ] **Step 1: 先写 H2 集成测试，验证 `A-13` 查询和 `A-16` 撤销**

```java
@Test
void shouldPageRoleAssignmentsWithJoinedUserAndRoleInfo() {
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, password_hash, status) VALUES (?,?,?,?,?)",
            1010L, "2024305001", "王老师", "x", "ACTIVE");
    jdbcTemplate.update(
            "INSERT INTO iam_user_role_assignment (id, user_id, role_id, org_unit_id, source_type, effective_from, effective_to, status, assigned_by, created_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
            70021L, 1010L, 21L, 2002L, "MANUAL", java.sql.Timestamp.valueOf("2026-05-18 00:00:00"),
            java.sql.Timestamp.valueOf("2027-07-01 00:00:00"), "ACTIVE", 9001L
    );

    PageResult<IamRoleAssignmentAdminRecord> result = repository.pageAssignments(
            new RoleAssignmentAdminPageQuery(1, 20, 1010L, "COUNSELOR", "ACTIVE", 2002L)
    );

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.records().getFirst().userNo()).isEqualTo("2024305001");
}

@Test
void shouldRevokeAssignmentToInactive() {
    jdbcTemplate.update(
            "INSERT INTO iam_user_role_assignment (id, user_id, role_id, org_unit_id, source_type, effective_from, effective_to, status, assigned_by, created_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
            70021L, 1010L, 21L, 2002L, "MANUAL", java.sql.Timestamp.valueOf("2026-05-18 00:00:00"),
            java.sql.Timestamp.valueOf("2027-07-01 00:00:00"), "ACTIVE", 9001L
    );

    repository.revoke(70021L);

    assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM iam_user_role_assignment WHERE id = ?", String.class, 70021L
    )).isEqualTo("INACTIVE");
}
```

- [ ] **Step 2: 运行集成测试，确认 H2 DDL / SQL join 条件正确**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=MybatisPlusRoleAssignmentAdminRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS；如果 FAIL，应优先修 SQL，不要在 service 层打补丁

- [ ] **Step 3: 回跑第一阶段全部定向测试**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=IamRoleAssignmentAdminControllerWebMvcTest,DefaultRoleAssignmentAdminApplicationServiceTest,MybatisPlusRoleAssignmentAdminRepositoryTest,MybatisPlusRoleAssignmentAdminRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS，且 `A-14/A-15/A-17/A-18` 旧测试不回归

- [ ] **Step 4: 检查编译与诊断**

Run:

```bash
mvn -pl whut-eval-app -am -DskipTests compile
```

Expected: BUILD SUCCESS

- [ ] **Step 5: 提交第一阶段代码**

```bash
git add \
  whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/RoleAssignmentAdminPageQuery.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/RoleAssignmentAdminPageItemView.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAssignmentAdminApplicationService.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/DefaultRoleAssignmentAdminApplicationService.java \
  whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/query/RoleAssignmentAdminPageQuery.java \
  whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/model/IamRoleAssignmentAdminRecord.java \
  whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/RoleAssignmentAdminRepository.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/IamUserRoleAssignmentMapper.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusRoleAssignmentAdminRepository.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/RoleAssignmentAdminPageRow.java \
  whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/IamRoleAssignmentAdminController.java \
  whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/response/RoleAssignmentPageItemResponse.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/iam/IamRoleAssignmentAdminControllerWebMvcTest.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/iam/DefaultRoleAssignmentAdminApplicationServiceTest.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/infra/MybatisPlusRoleAssignmentAdminRepositoryTest.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/iam/MybatisPlusRoleAssignmentAdminRepositoryIntegrationTest.java
git commit -m "feat: complete phase1 role assignment admin flow"
```

## Self Review

- Spec coverage:
  - `A-13` 已覆盖：controller 分页路由、application service 编排、repository 分页 SQL、unit/integration tests
  - `A-16` 已覆盖：controller 删除路由、application service 撤销语义、repository 状态更新、unit/integration tests
  - 未纳入本计划：`A-20/A-21/A-22/A-23` 及之后阶段，属于下一阶段任务
- Placeholder scan:
  - 未使用 `TODO/TBD/后续补` 之类占位词
  - 所有变更步骤都给出了目标文件、最小代码和验证命令
- Type consistency:
  - 分页查询对象明确拆成 application 层和 domain 层两套，避免 repo 反向依赖 application
  - `A-13` 列表响应使用新建 `RoleAssignmentPageItemResponse`，不污染现有 `RoleAssignmentResponse`
  - `A-16` 保持 `ApiResponse<Void>`，与当前项目 `ApiResponse.success(null)` 口径一致

Plan complete and saved to `docs/superpowers/plans/2026-05-19-a-group-phase1-role-assignment-closure.md`. Two execution options:

**1. Subagent-Driven (recommended)** - 我按任务逐个派子代理执行，每步回看结果再继续

**2. Inline Execution** - 我在当前会话直接按这份清单顺序落代码并验收

Which approach?
