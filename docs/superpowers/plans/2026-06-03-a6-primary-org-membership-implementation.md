# A-6 Primary Org Membership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在创建用户时落库 `primaryOrgUnitId` 到 `org_membership`，并在组织 ID 非法时返回 404 语义。

**Architecture:** 在 `UserAdminApplicationService#createUser` 增加编排：可选组织存在性校验 + 创建用户 + 可选主归属写入，全部在同一事务内完成。组织查询复用 `OrgUnitLookupRepository`，归属写入复用 `UserMembershipAdminRepository` 并新增单条主归属插入方法。通过 application/webmvc/repository 三层测试保证契约和落库正确性。

**Tech Stack:** Java 21+, Spring Boot 3.3, MyBatis-Plus, JUnit 5, Mockito, H2, Maven Surefire。

---

## File Structure (before tasks)

- Modify: `whut-eval-domain/src/main/java/edu/whut/eval/domain/org/repository/UserMembershipAdminRepository.java`
  - 新增单条主归属写入接口，避免在 createUser 路径复用 replace 语义。
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusUserMembershipAdminRepository.java`
  - 实现主归属插入，复用现有 `OrgMembershipDO`。
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`
  - 注入 `OrgUnitLookupRepository` + `UserMembershipAdminRepository`；在 `createUser` 内做组织校验与主归属写入。
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminApplicationServiceTest.java`
  - 补 A-6 应用层成功/失败单测，并更新构造参数。
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerWebMvcTest.java`
  - 补 createUser 404 语义测试。
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/MybatisPlusUserMembershipAdminRepositoryIntegrationTest.java`
  - 补主归属插入字段正确性测试。
- Modify: `docs/team-delivery/group-a-identity-user-admin-relay-checklist.md`
  - A-6 状态更新、完成说明和接力记录。

---

### Task 1: 扩展组织归属仓储接口与基础设施实现

**Files:**
- Modify: `whut-eval-domain/src/main/java/edu/whut/eval/domain/org/repository/UserMembershipAdminRepository.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusUserMembershipAdminRepository.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/MybatisPlusUserMembershipAdminRepositoryIntegrationTest.java`

- [ ] **Step 1: 写 repository integration 失败用例（先红）**

```java
@Test
void shouldCreatePrimaryMembershipForUser() {
    repository.createPrimaryMembership(1010L, 2011L, "2026-06-03T10:00:00");

    Map<String, Object> created = jdbcTemplate.queryForMap(
            "SELECT user_id, org_unit_id, membership_type, is_primary, status, joined_at, left_at " +
                    "FROM org_membership WHERE user_id = ? AND org_unit_id = ? AND status = 'ACTIVE'",
            1010L,
            2011L
    );

    assertThat(created.get("user_id")).isEqualTo(1010L);
    assertThat(created.get("org_unit_id")).isEqualTo(2011L);
    assertThat(created.get("membership_type")).isEqualTo("MANUAL");
    assertThat(created.get("is_primary")).isIn(true, 1);
    assertThat(created.get("status")).isEqualTo("ACTIVE");
    assertThat(created.get("left_at")).isNull();
}
```

- [ ] **Step 2: 运行单测确认失败**

Run:
```bash
mvn -pl whut-eval-app -am -Dtest=MybatisPlusUserMembershipAdminRepositoryIntegrationTest#shouldCreatePrimaryMembershipForUser test
```
Expected: FAIL，提示 `createPrimaryMembership` 未定义或未实现。

- [ ] **Step 3: 最小实现接口 + infra 方法**

`UserMembershipAdminRepository.java`:
```java
public interface UserMembershipAdminRepository {

    void lockUserForMembershipReplace(Long userId);

    List<OrgMembership> findActiveMembershipsByUserId(Long userId);

    void replaceMemberships(Long userId, List<OrgMembership> activeMemberships, List<OrgMembership> inactiveMemberships);

    void createPrimaryMembership(Long userId, Long orgUnitId, String joinedAt);
}
```

`MybatisPlusUserMembershipAdminRepository.java`:
```java
@Override
public void createPrimaryMembership(Long userId, Long orgUnitId, String joinedAt) {
    OrgMembershipDO membershipDO = new OrgMembershipDO();
    membershipDO.setUserId(userId);
    membershipDO.setOrgUnitId(orgUnitId);
    membershipDO.setMembershipType("MANUAL");
    membershipDO.setPrimary(true);
    membershipDO.setStatus("ACTIVE");
    membershipDO.setJoinedAt(parseTime(joinedAt));
    membershipDO.setLeftAt(null);
    membershipDO.setCreatedAt(parseTime(joinedAt));
    orgMembershipMapper.insert(membershipDO);
}
```

- [ ] **Step 4: 运行 repository 相关测试确认通过**

Run:
```bash
mvn -pl whut-eval-app -am -Dtest=MybatisPlusUserMembershipAdminRepositoryIntegrationTest test
```
Expected: PASS（含新用例）。

- [ ] **Step 5: Commit**

```bash
git add \
  whut-eval-domain/src/main/java/edu/whut/eval/domain/org/repository/UserMembershipAdminRepository.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusUserMembershipAdminRepository.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/iam/MybatisPlusUserMembershipAdminRepositoryIntegrationTest.java

git commit -m "feat(iam): add primary org membership repository write path"
```

**Rollback point:** 回滚到本任务 commit 前，系统行为恢复为“仅 replaceMemberships 路径可写入 org_membership”。

---

### Task 2: 在创建用户应用服务实现 A-6 编排

**Files:**
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminApplicationServiceTest.java`

- [ ] **Step 1: 写 application 层失败测试（先红）**

在 `UserAdminApplicationServiceTest` 新增两个用例：

```java
@Test
void shouldCreatePrimaryMembershipWhenPrimaryOrgUnitIdProvided() {
    IamUserQueryRepository queryRepository = mock(IamUserQueryRepository.class);
    IamUserCommandRepository commandRepository = mock(IamUserCommandRepository.class);
    SessionRevocationService revocationService = mock(SessionRevocationService.class);
    UserImportParser userImportParser = mock(UserImportParser.class);
    OrgUnitLookupRepository orgUnitLookupRepository = mock(OrgUnitLookupRepository.class);
    UserMembershipAdminRepository userMembershipAdminRepository = mock(UserMembershipAdminRepository.class);

    UserAdminApplicationService service = new UserAdminApplicationService(
            queryRepository,
            commandRepository,
            revocationService,
            userImportParser,
            orgUnitLookupRepository,
            userMembershipAdminRepository
    );

    when(queryRepository.findByUserNo("2024305111")).thenReturn(Optional.empty());
    when(orgUnitLookupRepository.findById(2002L))
            .thenReturn(Optional.of(new OrgUnit(2002L, 1000L, "COLLEGE", "CS", "计算机学院", "/1000/2002", "ACTIVE")));
    when(commandRepository.createUser(any(), any(), any(), any(), any()))
            .thenReturn(new IamUser(1011L, "2024305111", "李老师", "li@example.com", "13800001111", "ACTIVE"));

    service.createUser(new CreateUserCommand(
            "2024305111", "李老师", "secret123", "li@example.com", "13800001111", 2002L
    ));

    verify(userMembershipAdminRepository).createPrimaryMembership(eq(1011L), eq(2002L), anyString());
}

@Test
void shouldThrowNotFoundWhenPrimaryOrgUnitIdInvalid() {
    IamUserQueryRepository queryRepository = mock(IamUserQueryRepository.class);
    IamUserCommandRepository commandRepository = mock(IamUserCommandRepository.class);
    SessionRevocationService revocationService = mock(SessionRevocationService.class);
    UserImportParser userImportParser = mock(UserImportParser.class);
    OrgUnitLookupRepository orgUnitLookupRepository = mock(OrgUnitLookupRepository.class);
    UserMembershipAdminRepository userMembershipAdminRepository = mock(UserMembershipAdminRepository.class);

    UserAdminApplicationService service = new UserAdminApplicationService(
            queryRepository,
            commandRepository,
            revocationService,
            userImportParser,
            orgUnitLookupRepository,
            userMembershipAdminRepository
    );

    when(queryRepository.findByUserNo("2024305111")).thenReturn(Optional.empty());
    when(orgUnitLookupRepository.findById(9999L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> service.createUser(new CreateUserCommand(
            "2024305111", "李老师", "secret123", "li@example.com", "13800001111", 9999L
    )));

    verify(commandRepository, never()).createUser(any(), any(), any(), any(), any());
    verify(userMembershipAdminRepository, never()).createPrimaryMembership(any(), any(), any());
}
```

- [ ] **Step 2: 运行单测确认失败**

Run:
```bash
mvn -pl whut-eval-app -am -Dtest=UserAdminApplicationServiceTest test
```
Expected: FAIL（构造器参数不匹配或 createUser 未调用新仓储）。

- [ ] **Step 3: 修改应用服务最小实现**

`UserAdminApplicationService.java` 关键改动（imports + 字段 + 构造 + createUser）：

```java
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import edu.whut.eval.domain.org.repository.UserMembershipAdminRepository;
import java.time.LocalDateTime;

private final OrgUnitLookupRepository orgUnitLookupRepository;
private final UserMembershipAdminRepository userMembershipAdminRepository;

public UserAdminApplicationService(IamUserQueryRepository userQueryRepository,
                                   IamUserCommandRepository userCommandRepository,
                                   SessionRevocationService sessionRevocationService,
                                   UserImportParser userImportParser,
                                   OrgUnitLookupRepository orgUnitLookupRepository,
                                   UserMembershipAdminRepository userMembershipAdminRepository) {
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.sessionRevocationService = sessionRevocationService;
    this.userImportParser = userImportParser;
    this.orgUnitLookupRepository = orgUnitLookupRepository;
    this.userMembershipAdminRepository = userMembershipAdminRepository;
}

@Transactional
public UserCreatedView createUser(CreateUserCommand command) {
    userQueryRepository.findByUserNo(command.userNo()).ifPresent(u -> {
        throw new ConflictException("用户编号已存在: " + command.userNo());
    });

    if (command.primaryOrgUnitId() != null) {
        orgUnitLookupRepository.findById(command.primaryOrgUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("组织不存在: " + command.primaryOrgUnitId()));
    }

    String passwordHash = hashPassword(command.passwordHash());
    IamUser user = userCommandRepository.createUser(
            command.userNo(),
            command.userName(),
            passwordHash,
            command.email(),
            command.phone()
    );

    if (command.primaryOrgUnitId() != null) {
        userMembershipAdminRepository.createPrimaryMembership(
                user.id(),
                command.primaryOrgUnitId(),
                LocalDateTime.now().toString()
        );
    }

    return new UserCreatedView(user.id(), user.userNo(), user.userName(), user.status());
}
```

同时把该测试文件内所有 `new UserAdminApplicationService(...)` 构造调用统一补齐新增两个 mock 参数。

- [ ] **Step 4: 运行应用层测试确认通过**

Run:
```bash
mvn -pl whut-eval-app -am -Dtest=UserAdminApplicationServiceTest test
```
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add \
  whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminApplicationServiceTest.java

git commit -m "feat(iam): persist primary org membership on user creation"
```

**Rollback point:** 回滚本任务 commit 后，A-6 行为失效但不影响 A-5/A-8 已合并能力。

---

### Task 3: 补接口层 404 契约测试

**Files:**
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerWebMvcTest.java`

- [ ] **Step 1: 写 WebMvc 失败测试（先红）**

```java
@Test
void shouldReturn404WhenCreateUserPrimaryOrgUnitNotFound() throws Exception {
    CreateUserRequest request = new CreateUserRequest();
    request.setUserNo("2024305111");
    request.setUserName("李老师");
    request.setPassword("secret123");
    request.setEmail("li@example.com");
    request.setPhone("13800001111");
    request.setPrimaryOrgUnitId(9999L);

    willThrow(new ResourceNotFoundException("组织不存在: 9999"))
            .given(userAdminApplicationService)
            .createUser(any());

    mockMvc.perform(post("/api/admin/users")
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("RES-4040"));
}
```

- [ ] **Step 2: 运行 WebMvc 测试确认失败**

Run:
```bash
mvn -pl whut-eval-app -am -Dtest=UserAdminControllerWebMvcTest#shouldReturn404WhenCreateUserPrimaryOrgUnitNotFound test
```
Expected: FAIL（若缺 import 或行为未覆盖）。

- [ ] **Step 3: 最小修复测试依赖与导入**

确保 `UserAdminControllerWebMvcTest.java` 含以下 import：

```java
import edu.whut.eval.common.exception.ResourceNotFoundException;
```

并保留已有 `GlobalExceptionHandler` 配置，不新增 controller 代码。

- [ ] **Step 4: 运行 WebMvc 全套确认通过**

Run:
```bash
mvn -pl whut-eval-app -am -Dtest=UserAdminControllerWebMvcTest test
```
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerWebMvcTest.java

git commit -m "test(iam): cover 404 when primaryOrgUnitId is invalid"
```

**Rollback point:** 回滚本任务仅丢失契约测试，不影响运行时功能。

---

### Task 4: 全链路回归、文档更新与交付校验

**Files:**
- Modify: `docs/team-delivery/group-a-identity-user-admin-relay-checklist.md`

- [ ] **Step 1: 更新接力清单 A-6 状态与完成说明**

将 A-6 状态改为 `[x]`，完成说明补充：

```markdown
- 创建用户时若传 `primaryOrgUnitId`，同事务写入 `org_membership` 主组织记录
- 非法组织 ID 抛 `ResourceNotFoundException`，接口返回 404（RES-4040）
- 最小验证：UserAdminApplicationServiceTest / UserAdminControllerWebMvcTest / MybatisPlusUserMembershipAdminRepositoryIntegrationTest
```

并在“接力记录”追加一行：

```markdown
| 2026-06-03 | Claude | A-6 | 创建用户补 primaryOrgUnitId 落库与 404 语义 |
```

- [ ] **Step 2: 运行 A-6 定向测试集**

Run:
```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=UserAdminApplicationServiceTest,UserAdminControllerWebMvcTest,MybatisPlusUserMembershipAdminRepositoryIntegrationTest test
```
Expected: BUILD SUCCESS。

- [ ] **Step 3: 运行扩展回归（防止 A-5/A-8 回归）**

Run:
```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=UserAdminControllerSecurityAnnotationTest,MybatisPlusIamUserCommandRepositoryIntegrationTest,MybatisPlusIamUserQueryRepositoryIntegrationTest,ExcelUserImportParserTest test
```
Expected: BUILD SUCCESS。

- [ ] **Step 4: 生成交付摘要（用于 PR 描述）**

```markdown
## A-6 验证结果
- 创建用户支持 primaryOrgUnitId 主归属落库
- 非法组织 ID 返回 404（RES-4040）
- 事务一致性：用户与主归属同事务

## 测试
- UserAdminApplicationServiceTest: PASS
- UserAdminControllerWebMvcTest: PASS
- MybatisPlusUserMembershipAdminRepositoryIntegrationTest: PASS
```

- [ ] **Step 5: Commit**

```bash
git add docs/team-delivery/group-a-identity-user-admin-relay-checklist.md

git commit -m "docs(team): mark A-6 completed with verification notes"
```

**Rollback point:** 回滚本任务仅影响文档和验证记录，不影响功能。

---

## 测试计划总览

1. Repository 层：验证主归属插入字段正确。
2. Application 层：验证 createUser 编排（成功插入 / 非法组织 404 语义）。
3. WebMvc 层：验证异常映射为 `RES-4040` + HTTP 404。
4. 定向回归：验证 A-6 改动未破坏 A-5/A-8 既有链路。

---

## 回滚策略

- **代码级回滚**：按任务 commit 粒度回滚（Task 3 → Task 2 → Task 1）。
- **风险最小化顺序**：先回滚应用层（Task 2）可立即恢复创建用户旧行为；保留 Task 1 不影响线上路径。
- **数据回滚（如需）**：若已插入错误 membership，按 `user_id + org_unit_id + membership_type='MANUAL' + is_primary=1` 精确清理。

---

## 验收清单

- [ ] `POST /api/admin/users` 传合法 `primaryOrgUnitId`，创建后 `org_membership` 存在主归属记录。
- [ ] `POST /api/admin/users` 传不存在 `primaryOrgUnitId`，返回 HTTP 404 + `code=RES-4040`。
- [ ] 不传 `primaryOrgUnitId` 时创建用户行为保持兼容。
- [ ] A-6 定向测试与扩展回归全部通过。
- [ ] 接力清单 A-6 状态更新为已完成并附完成说明。

---

## Spec Coverage Self-Review

- 覆盖项 1（主归属写入）：Task 1 + Task 2。
- 覆盖项 2（非法组织 404）：Task 2 + Task 3。
- 覆盖项 3（最小改动 + 不扩范围）：Task 3 与 Out-of-Scope 约束已保持。
- 占位符检查：无 TBD/TODO。
- 命名一致性：`createPrimaryMembership` 在 domain/infra/test 中一致。
