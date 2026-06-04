# Group A P0 (A-5 + A-8) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver P0 in two serial commits: first make `/api/admin/users` query real DB data with `keyword/status/orgUnitId`; then make `/api/admin/users/import` perform real import with strict `INSERT_ONLY` conflict rollback semantics.

**Architecture:** Keep current controller → application service → domain repository flow. For A-5, remove placeholder paging and push filters into `IamUserQueryRepository` (keyword OR match + status + org filter). For A-8, keep `ImportUsersCommand(byte[])` orchestration in application service, introduce an import parser abstraction + infra implementation, and extend command repository minimally for UPSERT updates.

**Tech Stack:** Spring Boot 3.3, MyBatis-Plus, H2 integration tests, MockMvc, Mockito, Apache POI (for Excel parsing in infra).

---

## 0) File structure and responsibility map

### Existing files to modify
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserAdminController.java`
  - A-5 `keyword` parameter contract
  - A-8 file empty-check + mode validation guard
- `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/UserAdminPageQuery.java`
  - rename `userName` field to `keyword`
- `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`
  - A-5 real page query
  - A-8 real import orchestration
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/query/UserPageQuery.java`
  - add `keyword` + `orgUnitId`
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/IamUserCommandRepository.java`
  - add minimal UPSERT update method
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamUserQueryRepository.java`
  - A-5 filters (`keyword/status/orgUnitId`)
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamUserCommandRepository.java`
  - implement UPSERT update method
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/IamUserMapper.java`
  - add SQL update method for UPSERT path
- `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerWebMvcTest.java`
  - A-5 param expectation, A-8 error cases
- `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminApplicationServiceTest.java`
  - A-5 application paging mapping + A-8 orchestration tests
- `docs/team-delivery/group-a-identity-user-admin-relay-checklist.md`
  - mark A-5/A-8 completion details and relay record row

### New files to create
- `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserImportParser.java`
  - parser abstraction used by application service
- `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/UserImportRowView.java`
  - parsed-row DTO (rowNo + userNo/userName/password/email/phone)
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/iam/ExcelUserImportParser.java`
  - Apache POI-based parser implementation (existing template headers)
- `whut-eval-app/src/test/java/edu/whut/eval/app/iam/MybatisPlusIamUserQueryRepositoryIntegrationTest.java`
  - A-5 repository-level filter coverage
- `whut-eval-app/src/test/java/edu/whut/eval/app/iam/ExcelUserImportParserTest.java`
  - parser header/row parse tests
- `whut-eval-app/src/test/java/edu/whut/eval/app/iam/MybatisPlusIamUserCommandRepositoryIntegrationTest.java`
  - UPSERT update SQL behavior

### Dependency change
- `whut-eval-infra/pom.xml`
  - add Apache POI dependency for parser implementation.

---

### Task 1: A-5 contract switch to `keyword` (controller + web layer tests)

**Files:**
- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserAdminController.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerWebMvcTest.java`

- [ ] **Step 1: Write failing WebMvc test for keyword param passthrough**

```java
@Test
void shouldAcceptKeywordParamOnPageUsers() throws Exception {
    given(userAdminApplicationService.pageUsers(any()))
            .willReturn(new PageResult<>(0L, List.of()));

    mockMvc.perform(get("/api/admin/users")
                    .param("keyword", "王")
                    .param("status", "ACTIVE")
                    .param("orgUnitId", "2002"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
}
```

- [ ] **Step 2: Run test to verify failure**

Run:
```bash
mvn -pl whut-eval-app -Dtest=UserAdminControllerWebMvcTest#shouldAcceptKeywordParamOnPageUsers test
```
Expected: FAIL (controller still uses `userName`, no explicit keyword contract in code path).

- [ ] **Step 3: Update controller request param name and query construction**

```java
@GetMapping
public ApiResponse<PageResult<UserPageItemResponse>> pageUsers(
        @RequestParam(defaultValue = "1") long pageNo,
        @RequestParam(defaultValue = "20") long pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long orgUnitId) {

    UserAdminPageQuery query = new UserAdminPageQuery(pageNo, pageSize, keyword, status, orgUnitId);
    PageResult<UserAdminPageItemView> result = userAdminApplicationService.pageUsers(query);
    ...
}
```

- [ ] **Step 4: Run WebMvc tests**

Run:
```bash
mvn -pl whut-eval-app -Dtest=UserAdminControllerWebMvcTest test
```
Expected: PASS.

- [ ] **Step 5: Commit A-5 web contract**

```bash
git add whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserAdminController.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerWebMvcTest.java
git commit -m "feat: A-5 use keyword param on user paging endpoint"
```

---

### Task 2: A-5 application-layer real paging (remove placeholder)

**Files:**
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/UserAdminPageQuery.java`
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`
- Modify: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/query/UserPageQuery.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminApplicationServiceTest.java`

- [ ] **Step 1: Write failing application tests for real paging invocation**

```java
@Test
void shouldQueryRepositoryForPagedUsers() {
    IamUserQueryRepository queryRepository = mock(IamUserQueryRepository.class);
    IamUserCommandRepository commandRepository = mock(IamUserCommandRepository.class);
    SessionRevocationService revocationService = mock(SessionRevocationService.class);
    UserImportParser parser = mock(UserImportParser.class);

    UserAdminApplicationService service = new UserAdminApplicationService(
            queryRepository, commandRepository, revocationService, parser
    );

    UserPageQuery domainQuery = new UserPageQuery();
    domainQuery.setPageNo(1);
    domainQuery.setPageSize(20);
    domainQuery.setKeyword("王");
    domainQuery.setStatus("ACTIVE");
    domainQuery.setOrgUnitId(2002L);

    when(queryRepository.pageUsers(any())).thenReturn(new PageResult<>(0L, List.of()));

    PageResult<UserAdminPageItemView> result = service.pageUsers(new UserAdminPageQuery(1, 20, "王", "ACTIVE", 2002L));

    assertThat(result.total()).isEqualTo(0L);
    verify(queryRepository).pageUsers(any(UserPageQuery.class));
}
```

- [ ] **Step 2: Run test to verify failure**

Run:
```bash
mvn -pl whut-eval-app -Dtest=UserAdminApplicationServiceTest#shouldQueryRepositoryForPagedUsers test
```
Expected: FAIL (service currently returns fixed empty placeholder without repository invocation).

- [ ] **Step 3: Implement minimal A-5 service/query mapping**

```java
public record UserAdminPageQuery(
        long pageNo,
        long pageSize,
        String keyword,
        String status,
        Long orgUnitId
) {}
```

```java
@Transactional(readOnly = true)
public PageResult<UserAdminPageItemView> pageUsers(UserAdminPageQuery query) {
    UserPageQuery domainQuery = new UserPageQuery();
    domainQuery.setPageNo(query.pageNo());
    domainQuery.setPageSize(query.pageSize());
    domainQuery.setKeyword(query.keyword());
    domainQuery.setStatus(query.status());
    domainQuery.setOrgUnitId(query.orgUnitId());

    PageResult<IamUser> page = userQueryRepository.pageUsers(domainQuery);
    return new PageResult<>(
            page.total(),
            page.records().stream()
                    .map(user -> new UserAdminPageItemView(
                            user.id(), user.userNo(), user.userName(), user.status(), List.of(), List.of(), null
                    ))
                    .toList()
    );
}
```

```java
public class UserPageQuery {
    private long pageNo = 1;
    private long pageSize = 20;
    private String keyword;
    private String status;
    private Long orgUnitId;
    // getters/setters
}
```

- [ ] **Step 4: Run application tests**

Run:
```bash
mvn -pl whut-eval-app -Dtest=UserAdminApplicationServiceTest test
```
Expected: PASS (existing status tests + new paging test).

- [ ] **Step 5: Commit A-5 application layer**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/UserAdminPageQuery.java \
        whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java \
        whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/query/UserPageQuery.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminApplicationServiceTest.java
git commit -m "feat: A-5 query users from repository instead of placeholder"
```

---

### Task 3: A-5 infra filtering (`keyword/status/orgUnitId`) + integration tests

**Files:**
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamUserQueryRepository.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/MybatisPlusIamUserQueryRepositoryIntegrationTest.java`

- [ ] **Step 1: Write failing repository integration tests**

```java
@Test
void shouldFilterByKeywordOnUserNoAndUserName() {
    UserPageQuery query = new UserPageQuery();
    query.setPageNo(1);
    query.setPageSize(20);
    query.setKeyword("2024305001");

    PageResult<IamUser> page = repository.pageUsers(query);

    assertThat(page.total()).isEqualTo(1L);
    assertThat(page.records()).extracting(IamUser::userNo).containsExactly("2024305001");
}

@Test
void shouldFilterByStatusAndOrgUnitId() {
    UserPageQuery query = new UserPageQuery();
    query.setPageNo(1);
    query.setPageSize(20);
    query.setStatus("ACTIVE");
    query.setOrgUnitId(2002L);

    PageResult<IamUser> page = repository.pageUsers(query);

    assertThat(page.records()).allMatch(user -> "ACTIVE".equals(user.status()));
}
```

- [ ] **Step 2: Run test to verify failure**

Run:
```bash
mvn -pl whut-eval-app -Dtest=MybatisPlusIamUserQueryRepositoryIntegrationTest test
```
Expected: FAIL (keyword only matches `user_name`, no org filter).

- [ ] **Step 3: Implement repository filter logic**

```java
@Override
public PageResult<IamUser> pageUsers(UserPageQuery query) {
    Page<IamUserDO> page = Page.of(query.getPageNo(), query.getPageSize());
    LambdaQueryWrapper<IamUserDO> wrapper = new LambdaQueryWrapper<>();

    if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
        wrapper.and(w -> w.like(IamUserDO::getUserNo, query.getKeyword())
                          .or()
                          .like(IamUserDO::getUserName, query.getKeyword()));
    }
    wrapper.eq(query.getStatus() != null && !query.getStatus().isBlank(), IamUserDO::getStatus, query.getStatus());
    wrapper.inSql(query.getOrgUnitId() != null,
            IamUserDO::getId,
            "SELECT user_id FROM org_membership WHERE status = 'ACTIVE' AND org_unit_id = " + query.getOrgUnitId());
    wrapper.orderByAsc(IamUserDO::getId);

    Page<IamUserDO> result = iamUserMapper.selectPage(page, wrapper);
    return new PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toDomain).toList());
}
```

- [ ] **Step 4: Run integration + related tests**

Run:
```bash
mvn -pl whut-eval-app -Dtest=MybatisPlusIamUserQueryRepositoryIntegrationTest,UserAdminApplicationServiceTest,UserAdminControllerWebMvcTest test
```
Expected: PASS.

- [ ] **Step 5: Commit A-5 infra filtering**

```bash
git add whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamUserQueryRepository.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/iam/MybatisPlusIamUserQueryRepositoryIntegrationTest.java
git commit -m "feat: A-5 support keyword status orgUnit filters in user paging"
```

---

### Task 4: A-5 acceptance + checklist update (commit boundary #1)

**Files:**
- Modify: `docs/team-delivery/group-a-identity-user-admin-relay-checklist.md`

- [ ] **Step 1: Run A-5 final verification command set**

Run:
```bash
mvn -pl whut-eval-app -Dtest=UserAdminControllerWebMvcTest,UserAdminApplicationServiceTest,MybatisPlusIamUserQueryRepositoryIntegrationTest test
```
Expected: BUILD SUCCESS.

- [ ] **Step 2: Update checklist item #1**

Edit section `### 1. A-5 分页查询用户改为真实查库`:
- set status to `[x]`
- fill completion note with:
  - code files changed
  - test command + pass result
  - contract impact: switched query param to `keyword` (strict mode)

- [ ] **Step 3: Append relay record row**

```markdown
| 2026-06-02 | Claude | A-5 | /api/admin/users 改为真实查库，支持 keyword/status/orgUnitId |
```

- [ ] **Step 4: Commit checklist update for A-5**

```bash
git add docs/team-delivery/group-a-identity-user-admin-relay-checklist.md
git commit -m "docs: mark A-5 completed in relay checklist"
```

---

### Task 5: A-8 parser abstraction + failing import orchestration tests

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserImportParser.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/UserImportRowView.java`
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminApplicationServiceTest.java`

- [ ] **Step 1: Write failing application tests for A-8 behaviors**

```java
@Test
void shouldReturnRealStatsForUpsertImport() {
    // parser returns 3 rows; one invalid row -> failedRows includes rowNo+reason
    // repository create/update invoked accordingly
}

@Test
void shouldThrowConflictWhenInsertOnlyDetectsDuplicateUserNo() {
    // parser returns duplicate userNo existing in DB
    // expect ConflictException
}

@Test
void shouldRejectEmptyFile() {
    assertThrows(ValidationException.class,
            () -> service.importUsers(new ImportUsersCommand(new byte[0], "UPSERT")));
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:
```bash
mvn -pl whut-eval-app -Dtest=UserAdminApplicationServiceTest test
```
Expected: FAIL (import logic currently fixed `0/0/0`).

- [ ] **Step 3: Add parser contract and parsed-row DTO**

```java
public interface UserImportParser {
    List<UserImportRowView> parse(byte[] fileContent);
}
```

```java
public record UserImportRowView(
        long rowNo,
        String userNo,
        String userName,
        String password,
        String email,
        String phone
) {}
```

- [ ] **Step 4: Implement import orchestration in application service**

```java
@Transactional
public UserImportResultView importUsers(ImportUsersCommand command) {
    validateImportCommand(command);
    List<UserImportRowView> rows = userImportParser.parse(command.fileContent());

    if ("INSERT_ONLY".equals(command.importMode())) {
        List<String> duplicated = rows.stream()
                .map(UserImportRowView::userNo)
                .filter(Objects::nonNull)
                .filter(userNo -> userQueryRepository.findByUserNo(userNo).isPresent())
                .distinct()
                .toList();
        if (!duplicated.isEmpty()) {
            throw new ConflictException("INSERT_ONLY 模式存在重复 userNo: " + duplicated.get(0));
        }
    }

    long total = rows.size();
    long success = 0;
    List<UserImportFailedRowView> failedRows = new ArrayList<>();

    for (UserImportRowView row : rows) {
        String validationError = validateRow(row);
        if (validationError != null) {
            failedRows.add(new UserImportFailedRowView(row.rowNo(), validationError));
            continue;
        }
        upsertRow(row, command.importMode());
        success++;
    }

    return new UserImportResultView(total, success, failedRows.size(), failedRows);
}
```

- [ ] **Step 5: Run application tests**

Run:
```bash
mvn -pl whut-eval-app -Dtest=UserAdminApplicationServiceTest test
```
Expected: PASS.

- [ ] **Step 6: Commit A-8 application orchestration**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserImportParser.java \
        whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/UserImportRowView.java \
        whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminApplicationServiceTest.java
git commit -m "feat: A-8 implement import orchestration and conflict semantics"
```

---

### Task 6: A-8 infra parser (Excel) + UPSERT update persistence

**Files:**
- Modify: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/IamUserCommandRepository.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/IamUserMapper.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamUserCommandRepository.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/iam/ExcelUserImportParser.java`
- Modify: `whut-eval-infra/pom.xml`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/ExcelUserImportParserTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/MybatisPlusIamUserCommandRepositoryIntegrationTest.java`

- [ ] **Step 1: Write failing parser + UPSERT persistence tests**

```java
@Test
void shouldParseRowsFromExcelTemplate() {
    byte[] bytes = buildWorkbook("userNo","userName","password","email","phone", ...);
    List<UserImportRowView> rows = parser.parse(bytes);
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).rowNo()).isEqualTo(2L);
}

@Test
void shouldUpdateExistingUserByUserNo() {
    int updated = mapper.updateForImportByUserNo("2024305001", "新姓名", "newHash", "new@example.com", "13811112222");
    assertThat(updated).isEqualTo(1);
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:
```bash
mvn -pl whut-eval-app -Dtest=ExcelUserImportParserTest,MybatisPlusIamUserCommandRepositoryIntegrationTest test
```
Expected: FAIL (no parser implementation/update method yet).

- [ ] **Step 3: Add Apache POI dependency (infra module)**

```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

- [ ] **Step 4: Implement parser using existing template headers**

```java
@Component
public class ExcelUserImportParser implements UserImportParser {
    @Override
    public List<UserImportRowView> parse(byte[] fileContent) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileContent))) {
            Sheet sheet = workbook.getSheetAt(0);
            // validate header: userNo,userName,password,email,phone
            // map each non-empty data row -> UserImportRowView(rowNo,...)
        } catch (Exception ex) {
            throw new ValidationException("导入模板解析失败");
        }
    }
}
```

- [ ] **Step 5: Implement UPSERT update persistence method**

```java
@Update("""
UPDATE iam_user
SET user_name = #{userName},
    password_hash = #{passwordHash},
    email = #{email},
    phone = #{phone},
    updated_at = NOW()
WHERE user_no = #{userNo}
""")
int updateForImportByUserNo(@Param("userNo") String userNo,
                            @Param("userName") String userName,
                            @Param("passwordHash") String passwordHash,
                            @Param("email") String email,
                            @Param("phone") String phone);
```

```java
boolean updateForImportByUserNo(String userNo, String userName, String passwordHash, String email, String phone);
```

```java
@Override
public boolean updateForImportByUserNo(String userNo, String userName, String passwordHash, String email, String phone) {
    return iamUserMapper.updateForImportByUserNo(userNo, userName, passwordHash, email, phone) > 0;
}
```

- [ ] **Step 6: Run parser + persistence tests**

Run:
```bash
mvn -pl whut-eval-app -Dtest=ExcelUserImportParserTest,MybatisPlusIamUserCommandRepositoryIntegrationTest test
```
Expected: PASS.

- [ ] **Step 7: Commit A-8 infra implementation**

```bash
git add whut-eval-infra/pom.xml \
        whut-eval-infra/src/main/java/edu/whut/eval/infra/iam/ExcelUserImportParser.java \
        whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/IamUserCommandRepository.java \
        whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/IamUserMapper.java \
        whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamUserCommandRepository.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/iam/ExcelUserImportParserTest.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/iam/MybatisPlusIamUserCommandRepositoryIntegrationTest.java
git commit -m "feat: A-8 add excel parser and upsert update persistence"
```

---

### Task 7: A-8 controller validation + WebMvc/API behavior tests

**Files:**
- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserAdminController.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerWebMvcTest.java`

- [ ] **Step 1: Add failing WebMvc tests for empty file and conflict semantics**

```java
@Test
void shouldReturn400WhenImportFileEmpty() throws Exception {
    MockMultipartFile empty = new MockMultipartFile("file", "users.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

    mockMvc.perform(multipart("/api/admin/users/import").file(empty))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"));
}

@Test
void shouldReturn409WhenInsertOnlyConflict() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "dummy".getBytes());

    willThrow(new ConflictException("INSERT_ONLY 模式存在重复 userNo: 2024305001"))
            .given(userAdminApplicationService).importUsers(any());

    mockMvc.perform(multipart("/api/admin/users/import").file(file).param("importMode", "INSERT_ONLY"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("BIZ-4090"));
}
```

- [ ] **Step 2: Run WebMvc tests to verify failure**

Run:
```bash
mvn -pl whut-eval-app -Dtest=UserAdminControllerWebMvcTest test
```
Expected: FAIL (empty file currently not rejected in controller).

- [ ] **Step 3: Implement controller validation guard**

```java
@PostMapping("/import")
public ApiResponse<UserImportResultResponse> importUsers(@RequestParam("file") MultipartFile file,
                                                         @RequestParam(value = "importMode", defaultValue = "UPSERT") String importMode)
        throws IOException {
    if (file == null || file.isEmpty()) {
        throw new ValidationException("上传文件不能为空");
    }
    if (!"UPSERT".equals(importMode) && !"INSERT_ONLY".equals(importMode)) {
        throw new ValidationException("importMode 仅允许 UPSERT 或 INSERT_ONLY");
    }
    ...
}
```

- [ ] **Step 4: Run WebMvc tests**

Run:
```bash
mvn -pl whut-eval-app -Dtest=UserAdminControllerWebMvcTest,UserAdminControllerSecurityAnnotationTest test
```
Expected: PASS.

- [ ] **Step 5: Commit A-8 interface behavior**

```bash
git add whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserAdminController.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerWebMvcTest.java
git commit -m "feat: A-8 enforce import request validation and conflict behavior"
```

---

### Task 8: A-8 acceptance + checklist update (commit boundary #2)

**Files:**
- Modify: `docs/team-delivery/group-a-identity-user-admin-relay-checklist.md`

- [ ] **Step 1: Run A-8 final verification command set**

Run:
```bash
mvn -pl whut-eval-app -Dtest=UserAdminApplicationServiceTest,UserAdminControllerWebMvcTest,ExcelUserImportParserTest,MybatisPlusIamUserCommandRepositoryIntegrationTest test
```
Expected: BUILD SUCCESS.

- [ ] **Step 2: Update checklist item #2**

Edit section `### 2. A-8 批量导入用户改为真实导入`:
- set status to `[x]`
- completion note must include:
  - parser + service + repository files
  - verified counters/failedRows behavior
  - `INSERT_ONLY` duplicate => 409 + rollback semantics

- [ ] **Step 3: Append relay record row**

```markdown
| 2026-06-02 | Claude | A-8 | /api/admin/users/import 真实导入与 INSERT_ONLY 冲突回滚 |
```

- [ ] **Step 4: Commit checklist update for A-8**

```bash
git add docs/team-delivery/group-a-identity-user-admin-relay-checklist.md
git commit -m "docs: mark A-8 completed in relay checklist"
```

---

### Task 9: Full regression and delivery note

**Files:**
- Modify: `docs/team-delivery/group-a-identity-user-admin-relay-checklist.md` (if final notes need minor additions)

- [ ] **Step 1: Run consolidated targeted suite for A-5 + A-8**

Run:
```bash
mvn -pl whut-eval-app -Dtest=UserAdminControllerWebMvcTest,UserAdminApplicationServiceTest,MybatisPlusIamUserQueryRepositoryIntegrationTest,ExcelUserImportParserTest,MybatisPlusIamUserCommandRepositoryIntegrationTest,UserAdminControllerSecurityAnnotationTest test
```
Expected: BUILD SUCCESS.

- [ ] **Step 2: Sanity check changed API contracts**

Check:
- `GET /api/admin/users` query key is `keyword` only.
- `POST /api/admin/users/import` rejects empty file with 400.
- `INSERT_ONLY` duplicate path returns 409.

- [ ] **Step 3: Final summary commit if needed (no code changes expected)**

```bash
# If no changes, skip commit
# If minor doc touch exists:
git add docs/team-delivery/group-a-identity-user-admin-relay-checklist.md
git commit -m "docs: finalize P0 verification notes for A-5 and A-8"
```

---

## Commit plan (serial, mandatory)

### Commit set #1 (A-5 only)
- Scope: Task 1 → Task 4
- Acceptance criteria:
  - `/api/admin/users` uses `keyword` parameter
  - application no longer returns fixed empty page
  - repository supports `keyword/status/orgUnitId`
  - checklist item #1 marked complete

### Commit set #2 (A-8 only)
- Scope: Task 5 → Task 9
- Acceptance criteria:
  - import endpoint performs real parsing + import
  - response counters and failedRows are real values
  - `INSERT_ONLY` duplicate conflict returns 409 and rolls back
  - checklist item #2 marked complete

---

## Spec coverage self-review

- A-5 real paging: covered by Tasks 1–4.
- A-5 filter semantics (`keyword`, `status`, `orgUnitId`): covered by Task 3 integration tests.
- A-8 real import and stats: covered by Tasks 5–7.
- A-8 `INSERT_ONLY` conflict rollback: covered by Task 5 logic + Task 7 API behavior + Task 8 acceptance.
- Checklist backfill and relay records: covered by Tasks 4 and 8.

No `TODO`/`TBD` placeholders remain.
