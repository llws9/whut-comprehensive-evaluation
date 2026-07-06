# Minimal E Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the smallest E-group backend foundation needed to unblock B-group student application development.

**Architecture:** Keep Nacos typed config as the runtime source for platform rules and evaluation item reads, and use MySQL as the runtime source for file metadata and public attachment reads. Add B-facing read controllers and application services without implementing E-group target-state governance writes, item CRUD, public attachment publishing, scoped attachment visibility, or AI APIs.

**Tech Stack:** Java 21, Spring Boot, Spring Security, MyBatis annotations, MySQL/H2-compatible SQL tests, Maven, JUnit 5, MockMvc.

---

本 ExecPlan 是一份活文档。进度追踪、意外发现、决策日志 和 成果与复盘 章节必须随工作推进持续更新。

**创建时代码基线：**
- 分支：`main`
- Commit SHA：`3f9446268c83ab3468cddcec9b9ea71b1758aa40`
- 时区：`Asia/Shanghai`
- 提交策略：实现阶段按里程碑提交；本 proposal 计划不提交，等待用户 review。

## 目标与全局视角

改完后，B 组学生申请链路可以稳定读取平台开关、展示截止时间、获取启用的综测项目、上传个人附件、读取自己或公共池中的文件元信息，并在需要展示/下载文件时获取可用访问 URL。A 组 IAM 仍提供登录、当前用户、安全过滤器和权限上下文；Minimal E 只消费这些基础能力。

**需求对齐记录**（Phase 0 产出）：
- 用户原始需求：先做推荐的最小 E，完成 spec 后进入执行计划阶段。
- Agent 理解：只实现 B 组下一步需要的 E 基础读接口和文件基础能力，不做完整 E 平台治理。
- 已确认的边界：
  - 做：平台读、deadline 读、启用项目列表、上传安全响应、文件元信息读、文件 access-url 读、公共附件列表、非破坏性 E 表初始化。
  - 不做：平台治理写接口、项目 CRUD、公共附件发布/下线、角色/组织范围公共附件、AI、C/E 审核绑定附件授权。
- 关键澄清问答：
  - Q: Minimal E 是完整 E 组目标态还是只解锁 B 组？→ A: 只做最小 E，先解锁 B 组。
  - Q: 现有 E SQL 是否可以直接执行？→ A: 不能盲跑含 `DROP TABLE IF EXISTS` 的冻结脚本，需要安全初始化。

## 进度追踪

- [x] (2026-07-06 17:47:36+08:00) Phase 0: 需求对齐完成
- [x] (2026-07-06 17:47:36+08:00) Phase 2: 方案撰写完成
- [x] (2026-07-06 19:25:00+08:00) Phase 3: 用户 Review 通过
- [x] (2026-07-06 19:46:23+08:00) Milestone 1: 安全初始化 SQL 与 typed config 字段
- [x] (2026-07-06 19:51:37+08:00) Milestone 2: 平台读与项目列表接口
- [x] (2026-07-06 19:53:30+08:00) Milestone 3: 上传响应安全化
- [x] (2026-07-06 19:59:12+08:00) Milestone 4: 文件元信息、access-url、公共附件读取
- [x] (2026-07-06 20:00:49+08:00) Milestone 5: 安全过滤器验证与回归测试
- [x] (2026-07-06 20:00:49+08:00) Phase 5: 结果汇报
- [x] (2026-07-06 20:00:49+08:00) Phase 7: 代码提交/PR 合并

## 意外发现

- 观察：当前上传接口响应仍包含 `bucket`、`objectKey`、`publicUrl`。
  证据：`whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/file/FileUploadController.java` 的 `toView` 构造了包含存储字段的 `StoredFileDescriptorView`。
- 观察：`PlatformRuleConfig` 当前没有 deadline 字段。
  证据：`whut-eval-domain/src/main/java/edu/whut/eval/domain/config/model/PlatformRuleConfig.java` 只有 `studentApplyEnabled`、`finalSubmitEnabled`、`maxReviewBatchSize`。
- 观察：冻结 E SQL 与运行时 mapper 不完全一致。
  证据：`docs/team-delivery/group-e-platform-governance-attachment-ai.sql` 中 `file_asset.id` 非自增，`public_attachment_entry` 缺少 `updated_at`；但 `FileAssetWriteMapper` 期望 generated keys，`PublicAttachmentEntryMapper` 查询 `updated_at`。

## 决策日志

- 决策：新增 `group-e-platform-governance-attachment-ai.safe-init.sql`，不直接修改或运行冻结 SQL。
  理由：冻结 SQL 是交付基线，包含破坏性 drop；运行时需要可重复执行且不删除上传文件元数据。
  日期/作者：2026-07-06 / TRAE CLI
- 决策：B-facing `/api/platform/**` 读接口使用 authenticated access，不要求 E-admin 权限。
  理由：学生页面需要读平台开关、deadline 和项目目录；写侧治理接口仍然不在本轮范围。
  日期/作者：2026-07-06 / TRAE CLI
- 决策：文件读取授权放在 application service 层，mapper 只提供查询能力。
  理由：owner/public 可见性是业务规则，需要可单测；controller 不应承载授权分支。
  日期/作者：2026-07-06 / TRAE CLI

## 成果与复盘

在主要里程碑或全部完成时填写。

### 完成汇报（Phase 5 产出）

**目标达成**：已完成 Minimal E foundation 的后端实现与 focused regression 验证。

**变更概览**：
- 新增 B-facing `/api/platform/menu/status`、`/api/platform/menu/deadline`、`/api/platform/evaluation-items`。
- 新增文件元信息、access-url 和公共附件读取能力。
- 上传响应收窄为安全子集。
- 新增 E 组非破坏性 safe-init SQL，并补齐 `PlatformRuleConfig` deadline 字段。

**验收结果**：
- 测试：`mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=FileQueryControllerWebMvcTest,PlatformReadControllerWebMvcTest,EvaluationItemQueryControllerWebMvcTest,FileQueryApplicationServiceTest,PublicAttachmentQueryRepositoryIntegrationTest,MinimalEReadSecurityIntegrationTest,FileUploadControllerWebMvcTest,PlatformRuleConfigProviderTest,TeamDeliverySqlConsistencyTest test`，37 tests, 0 failures, 0 errors, 0 skipped，`BUILD SUCCESS`
- 编译：由 Maven focused regression 覆盖
- Lint：本仓库当前未发现独立 lint 命令，使用 Maven test/compile 作为刚性验证

**已知风险/遗留**：
- 安全测试必须启用 Spring Security filter chain；禁用 filter 的 `@WebMvcTest` 只能覆盖 mapping。
- 本计划不处理 C/E 审核基于 application binding 的附件读取权限。

**建议后续**：
- 完成 Minimal E 后，进入 B 组学生申请写链路联调。

## 上下文与方向

Minimal E 的 spec 文件是 `docs/superpowers/specs/2026-07-06-minimal-e-foundation-design.md`。它已经明确以下 endpoint 为本轮范围：

- `GET /api/platform/menu/status`
- `GET /api/platform/menu/deadline`
- `GET /api/platform/evaluation-items`
- 既有 `GET /api/config/evaluation/options/{itemCode}`
- 既有 `POST /api/files/upload`，但响应改为安全子集
- `GET /api/files/{fileId}`
- `GET /api/files/{fileId}/access-url`
- `GET /api/files/public-attachments`

关键现有代码：

- `whut-eval-domain/src/main/java/edu/whut/eval/domain/config/model/PlatformRuleConfig.java`：平台规则 typed config 模型。
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/config/model/EvaluationItemsConfig.java`：项目配置模型，已包含 `maxPoints`、`maxPointsExpression`、`optionsKey`。
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/config/model/OssStorageConfig.java`：OSS typed config，包含 `publicBaseUrl`。
- `whut-eval-application/src/main/java/edu/whut/eval/application/config/EvaluationConfigApplicationService.java`：现有 config 读取服务。
- `whut-eval-application/src/main/java/edu/whut/eval/application/file/service/FileUploadApplicationService.java`：上传编排服务，已通过 `UserAuthorizationContextAssembler` 获取当前用户。
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FileAssetMapper.java`：现有 `selectByFileIds` 查询。
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FileAssetWriteMapper.java`：上传写入 mapper，依赖 `file_asset.id` 自增。
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/PublicAttachmentEntryMapper.java`：公共附件 mapper，当前查询 `updated_at`。
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/config/EvaluationConfigController.java`：既有 `/api/config/evaluation/**` endpoint，保留。
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/file/FileUploadController.java`：既有上传 endpoint，需要收窄响应。
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/security/config/SecurityConfiguration.java`：全局 `anyRequest().authenticated()` 与安全过滤器。

关键术语：

- `fileId`：对客户端暴露的稳定业务文件 ID，存于 `file_asset.file_id`。
- `storage_key`：对象存储内部 key，只能在服务端和数据库中使用，不返回给 B 组客户端。
- `PUBLISHED + ALL`：本轮 B 组可消费的公共附件范围，来自 `public_attachment_entry.status` 和 `scope_type`。

## 工作计划

### Milestone 1: 安全初始化 SQL 与 typed config 字段

**范围**：补齐 `PlatformRuleConfig` deadline 字段，创建非破坏性 E 表初始化 SQL，并补 SQL 一致性测试。

**成果**：
- `docs/team-delivery/group-e-platform-governance-attachment-ai.safe-init.sql` 存在，使用 `CREATE TABLE IF NOT EXISTS` 和 guarded seed insert。
- `file_asset.id`、`public_attachment_entry.id` 使用 `AUTO_INCREMENT`。
- `public_attachment_entry.updated_at` 存在，seed insert 提供 `updated_at`。
- `PlatformRuleConfig` 支持 `studentApplyDeadline` 和 `finalSubmitDeadline`。

**命令**：

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PlatformRuleConfigProviderTest,TeamDeliverySqlConsistencyTest test
```

**验收**（刚性量化指标）：
- Maven 输出 `BUILD SUCCESS`。
- 两个测试类全部 PASS，0 failures，0 errors，0 skipped。
- 文本检查 `rg -n "DROP TABLE" docs/team-delivery/group-e-platform-governance-attachment-ai.safe-init.sql` 无输出。
- Safe-init SQL 使用 MySQL 与 H2 MySQL-mode 都可执行的 DDL/seed 子集；不包含运行时非必需的 `ENGINE=`、`CHARSET`、`COLLATE`、表/列 `COMMENT` 语法。
- Safe-init SQL 可重复执行：在 H2 MySQL-mode 中先插入一条非 seed 的 `file_asset` runtime row，连续执行 safe-init 两次后，该 runtime row 仍存在且 `storage_key`、`uploader_user_id`、`status` 未变化，seed row 数量不重复增长。

**Files:**
- Modify: `whut-eval-domain/src/main/java/edu/whut/eval/domain/config/model/PlatformRuleConfig.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/config/PlatformRuleConfigProviderTest.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java`
- Create: `docs/team-delivery/group-e-platform-governance-attachment-ai.safe-init.sql`

- [ ] **Step 1: Write failing tests for deadline fields and safe SQL**

Update `PlatformRuleConfigProviderTest.shouldMaterializePlatformRuleConfigFromYamlPayload` YAML payload:

```yaml
studentApplyEnabled: true
finalSubmitEnabled: false
maxReviewBatchSize: 100
studentApplyDeadline: "2026-09-30T23:59:59+08:00"
finalSubmitDeadline: "2026-10-15T23:59:59+08:00"
```

Add assertions:

```java
assertThat(config.getStudentApplyDeadline()).isEqualTo("2026-09-30T23:59:59+08:00");
assertThat(config.getFinalSubmitDeadline()).isEqualTo("2026-10-15T23:59:59+08:00");
```

Extend `TeamDeliverySqlConsistencyTest` with checks that the safe-init file exists, contains `CREATE TABLE IF NOT EXISTS`, does not contain `DROP TABLE`, contains `file_asset` and `public_attachment_entry` `AUTO_INCREMENT`, and inserts `public_attachment_entry.updated_at`.

Add a safe-init idempotency test in `TeamDeliverySqlConsistencyTest` using an H2 MySQL-mode in-memory database. The test must:
- execute the actual `docs/team-delivery/group-e-platform-governance-attachment-ai.safe-init.sql` file, not an equivalent DDL string copied into the test;
- execute only the safe-init SQL file, never the frozen destructive SQL;
- insert a runtime row such as `file_runtime_001` into `file_asset` after the first safe-init execution;
- execute the same safe-init SQL a second time;
- assert `file_runtime_001` still exists;
- assert `storage_key`, `uploader_user_id`, and `status` for `file_runtime_001` are unchanged;
- assert representative seed rows such as `FILE-0008` and public entry `14001` appear exactly once.

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PlatformRuleConfigProviderTest,TeamDeliverySqlConsistencyTest test
```

Expected before implementation: failures for missing getters and/or missing safe-init SQL.

- [ ] **Step 3: Implement deadline fields and safe-init SQL**

Add nullable string fields with getters/setters to `PlatformRuleConfig`:

```java
private String studentApplyDeadline;
private String finalSubmitDeadline;
```

Create `group-e-platform-governance-attachment-ai.safe-init.sql` by adapting the frozen SQL:
- remove all `DROP TABLE IF EXISTS`;
- use `CREATE TABLE IF NOT EXISTS`;
- set `file_asset.id BIGINT NOT NULL AUTO_INCREMENT`;
- set `public_attachment_entry.id BIGINT NOT NULL AUTO_INCREMENT`;
- add `public_attachment_entry.updated_at DATETIME NOT NULL`;
- use guarded seed insert patterns such as `INSERT INTO ... SELECT ... WHERE NOT EXISTS (...)`, with guards on business unique keys or explicit seed IDs;
- never update existing `file_asset` rows during seed initialization;
- provide `updated_at` in `public_attachment_entry` seed rows, equal to `created_at`.
- keep the SQL executable in both MySQL and H2 MySQL-mode so the idempotency test exercises the same file used for local initialization. Avoid runtime-optional MySQL-only decorations such as `ENGINE=InnoDB`, `DEFAULT CHARSET=...`, `COLLATE=...`, table or column `COMMENT`, and seed expressions like `JSON_OBJECT(...)` when a JSON string literal can represent the same seed semantics.

- [ ] **Step 4: Re-run Milestone 1 verification**

Run the command above again.

Expected: `BUILD SUCCESS`, 0 skipped.

### Milestone 2: 平台读与项目列表接口

**范围**：新增 B-facing platform read controller/service 和 evaluation item list endpoint，保留既有 `/api/config/evaluation/**`。

**成果**：
- `GET /api/platform/menu/status` 返回 Nacos platform switch。
- `GET /api/platform/menu/deadline` 返回 deadline 字符串或 null。
- `GET /api/platform/evaluation-items` 返回 flat enabled-only list，支持 `categoryCode`，排序稳定。

**命令**：

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PlatformReadControllerWebMvcTest,EvaluationItemQueryControllerWebMvcTest,EvaluationItemQueryApplicationServiceTest test
```

**验收**（刚性量化指标）：
- Maven 输出 `BUILD SUCCESS`。
- `evaluation-items` 测试断言无 `enabledOnly=false` 管理模式。
- unknown category 返回 HTTP 200 且 data 为空数组。

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/platform/query/PlatformMenuStatus.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/platform/query/PlatformMenuDeadline.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/platform/query/EvaluationItemResponse.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/platform/service/PlatformReadApplicationService.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/platform/PlatformReadController.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/platform/PlatformReadControllerWebMvcTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/platform/EvaluationItemQueryControllerWebMvcTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/platform/EvaluationItemQueryApplicationServiceTest.java`

- [ ] **Step 1: Write failing service and controller tests**

Service test cases:
- status maps `studentApplyEnabled`, `finalSubmitEnabled`, source `NACOS`;
- deadline returns configured strings and null when absent;
- item list filters disabled items;
- item list sorts by `categoryCode`, `sortOrder`, `itemCode`;
- `categoryCode` with no matches returns empty list.

Controller tests:
- authenticated mapping returns `ApiResponse` data for each endpoint;
- `GET /api/platform/evaluation-items?categoryCode=INTELLECTUAL` passes the filter to service;
- response type is flat array in `data`.

- [ ] **Step 2: Run tests to verify failure**

Run the Milestone 2 Maven command.

Expected before implementation: missing classes/controllers fail compilation.

- [ ] **Step 3: Implement platform read application service**

Inject `TypedConfigRepository` and read:
- `platform-rule-config` as `PlatformRuleConfig`;
- `evaluation-items-config` as `EvaluationItemsConfig`.

Throw `ConfigLoadException` when required config is missing. Build `EvaluationItemResponse` from Nacos fields only:

```java
categoryCode, categoryName, itemCode, itemName, description,
maxPoints, maxPointsExpression, applyMode, enabled, sortOrder, optionsKey
```

- [ ] **Step 4: Implement controller**

Create `PlatformReadController` under `/api/platform` with:
- `@GetMapping("/menu/status")`
- `@GetMapping("/menu/deadline")`
- `@GetMapping("/evaluation-items")`

Add `@PreAuthorize("isAuthenticated()")` at class or method level so tests make authenticated-read intent explicit.

- [ ] **Step 5: Re-run Milestone 2 verification**

Run the Milestone 2 Maven command.

Expected: `BUILD SUCCESS`, 0 skipped.

### Milestone 3: 上传响应安全化

**范围**：收窄 `POST /api/files/upload` 的 B-facing response，不改变上传写入链路。

**成果**：
- 上传响应仅包含 `fileId`、`originalFilename`、`contentType`、`size`。
- 响应不包含 `bucket`、`objectKey`、`storageKey`、`publicUrl`。
- `FileUploadApplicationService` 和 `FileAssetRegistry` 继续保留内部 descriptor 的存储字段。

**命令**：

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=FileUploadControllerWebMvcTest,FileUploadApplicationServiceTest,MybatisFileAssetRegistryIntegrationTest test
```

**验收**（刚性量化指标）：
- Maven 输出 `BUILD SUCCESS`。
- WebMvc 测试明确断言 `$.data.bucket`、`$.data.objectKey`、`$.data.storageKey`、`$.data.publicUrl` 不存在。
- upload-style insert 仍能写入 `file_asset` 并生成 `fileId`。

**Files:**
- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/file/FileUploadController.java`
- Replace or modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/file/view/StoredFileDescriptorView.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/file/FileUploadControllerWebMvcTest.java`

- [ ] **Step 1: Update failing upload response test**

Change successful upload assertions to:

```java
.andExpect(jsonPath("$.data.fileId").value("file_test_001"))
.andExpect(jsonPath("$.data.originalFilename").value("award.pdf"))
.andExpect(jsonPath("$.data.contentType").value("application/pdf"))
.andExpect(jsonPath("$.data.size").value(128))
.andExpect(jsonPath("$.data.bucket").doesNotExist())
.andExpect(jsonPath("$.data.objectKey").doesNotExist())
.andExpect(jsonPath("$.data.storageKey").doesNotExist())
.andExpect(jsonPath("$.data.publicUrl").doesNotExist());
```

- [ ] **Step 2: Run tests to verify failure**

Run the Milestone 3 Maven command.

Expected before implementation: response still contains storage fields.

- [ ] **Step 3: Implement safe upload response view**

Change `StoredFileDescriptorView` constructor/getters to expose only:
- `fileId`
- `originalFilename`
- `contentType`
- `size`

Update `FileUploadController.toView` accordingly.

- [ ] **Step 4: Re-run Milestone 3 verification**

Run the Milestone 3 Maven command.

Expected: `BUILD SUCCESS`, 0 skipped.

### Milestone 4: 文件元信息、access-url、公共附件读取

**范围**：新增 read-side file application service、repository methods、controller 和 tests。

**成果**：
- `GET /api/files/{fileId}` 支持 owner active file 和 `PUBLISHED + ALL` public file。
- `GET /api/files/{fileId}/access-url` 使用 `OssStorageConfig.publicBaseUrl` 和 `file_asset.storage_key` 生成 URL。
- `GET /api/files/public-attachments` 只返回 `PUBLISHED + ALL` 且 backing file active 的附件。

**命令**：

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=FileQueryControllerWebMvcTest,FileQueryApplicationServiceTest,PublicAttachmentQueryRepositoryIntegrationTest test
```

**验收**（刚性量化指标）：
- Maven 输出 `BUILD SUCCESS`。
- metadata owner/public reads HTTP 200。
- private non-owner read HTTP 403 with `AUTH-4030`。
- missing or inactive file HTTP 404 with `RES-4040`。
- missing `publicBaseUrl` returns HTTP 503 with `EXT-5033`。
- public attachment list filters out `ROLE`、`ORG_UNIT`、`DRAFT`、`OFFLINE`、inactive backing files。

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/file/query/FileMetadataResponse.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/file/query/FileAccessUrlResponse.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/file/query/PublicAttachmentResponse.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/file/query/PublicAttachmentDescriptor.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/file/service/FileQueryApplicationService.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/file/service/FileQueryRepository.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisFileQueryRepository.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FileAssetMapper.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/PublicAttachmentEntryMapper.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/file/FileQueryController.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/file/FileQueryControllerWebMvcTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/file/FileQueryApplicationServiceTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/file/PublicAttachmentQueryRepositoryIntegrationTest.java`

- [ ] **Step 1: Define minimal query contracts and skeletons**

Create response DTOs with final fields and getters:
- `FileMetadataResponse`: `fileId`, `originalFilename`, `contentType`, `size`, `status`, `createdAt`.
- `FileAccessUrlResponse`: `fileId`, `accessUrl`, `expiresAt`.
- `PublicAttachmentResponse`: `entryId`, `fileId`, `displayName`, `description`, `categoryCode`, `originalFilename`, `contentType`, `size`, `publishedAt`, `sortNo`.

Create `PublicAttachmentDescriptor` as the repository-facing query model with the same business fields as `PublicAttachmentResponse`. `MybatisFileQueryRepository` returns descriptors; `FileQueryApplicationService` maps descriptors into responses.

Create `FileQueryRepository` with:

```java
Optional<FileAssetDO> findActiveFileByFileId(String fileId);

boolean existsPublishedAllPublicAttachment(String fileId);

List<PublicAttachmentDescriptor> listPublishedAllPublicAttachments(String categoryCode);
```

Create `FileQueryApplicationService` with constructor dependencies and public method signatures only:

```java
public FileMetadataResponse getMetadata(String fileId);

public FileAccessUrlResponse getAccessUrl(String fileId);

public List<PublicAttachmentResponse> listPublicAttachments(String categoryCode);
```

Create `MybatisFileQueryRepository` as a Spring `@Repository` implementing `FileQueryRepository`. Add constructor parameters for the mapper dependencies, but keep each method body as:

```java
throw new UnsupportedOperationException("File query repository behavior is implemented in Milestone 4 Step 6");
```

Create `FileQueryController` under `/api/files` with constructor injection of `FileQueryApplicationService`, `@PreAuthorize("isAuthenticated()")`, and endpoint methods for:
- `GET /{fileId}`
- `GET /{fileId}/access-url`
- `GET /public-attachments`

Each controller method should return `ApiResponse.success(...)` from the corresponding service method. With this skeleton in place, controller tests compile and application service tests fail on unimplemented behavior rather than missing classes.

- [ ] **Step 2: Write failing application service tests**

Cover:
- owner active file returns metadata;
- public `PUBLISHED + ALL` active file returns metadata;
- non-owner private active file throws access denied;
- inactive file throws resource not found;
- access-url normalizes `https://cdn.example.com/base/` + `/uploads/a.pdf` to exactly one slash;
- blank or missing `publicBaseUrl` throws file storage failure.

- [ ] **Step 3: Write failing repository integration tests**

Create H2 MySQL-mode schema for `file_asset` and `public_attachment_entry` with runtime columns. Insert rows covering:
- owner file;
- public `ALL` file;
- `ROLE` public entry;
- `ORG_UNIT` public entry;
- `DRAFT` entry;
- `OFFLINE` entry;
- active public entry backed by inactive file.

Assert public attachment list order:
`sort_no ASC`, `published_at DESC`, `id ASC`.

- [ ] **Step 4: Write failing controller tests**

Mock `FileQueryApplicationService`; assert JSON shape:
- metadata has `fileId`, `originalFilename`, `contentType`, `size`, `status`, `createdAt`;
- metadata does not have `bucket`, `storageKey`, `uploaderUserId`;
- access-url has `fileId`, `accessUrl`, `expiresAt`;
- public attachments response is a flat list under `data`.

- [ ] **Step 5: Run tests to verify failure**

Run the Milestone 4 Maven command.

Expected before implementation: tests compile, then fail because `FileQueryApplicationService` methods throw `UnsupportedOperationException` and repository/controller behavior is not implemented.

- [ ] **Step 6: Implement repository and mapper methods**

Add mapper methods needed by `MybatisFileQueryRepository`:
- select active file by single `fileId`;
- test whether a `fileId` is published as `PUBLISHED + ALL`;
- list public attachments joined to active `file_asset`, optionally by `categoryCode`.

Map MyBatis join results into `PublicAttachmentDescriptor`, not `PublicAttachmentResponse`. Do not expose storage fields from application response types.

- [ ] **Step 7: Implement application service**

Inject:
- `UserAuthorizationContextAssembler`;
- `FileQueryRepository`;
- `OssStorageConfigProvider`.

Use `authorizationContext.getUserId()` for owner checks. Build access URL from `publicBaseUrl` and `storageKey`; return `expiresAt = null`.

- [ ] **Step 8: Implement controller**

Create `FileQueryController` under `/api/files` with:
- `GET /{fileId}`
- `GET /{fileId}/access-url`
- `GET /public-attachments`

Add `@PreAuthorize("isAuthenticated()")` at class or method level.

- [ ] **Step 9: Re-run Milestone 4 verification**

Run the Milestone 4 Maven command.

Expected: `BUILD SUCCESS`, 0 skipped.

### Milestone 5: 安全过滤器验证与全量 focused 回归

**范围**：增加启用 security filter chain 的最小 E 读安全测试，并运行 spec 指定 focused suite。

**成果**：
- authenticated non-admin 用户可以访问 B-facing read endpoints。
- anonymous 用户被安全层拒绝。
- 禁用 filter 的 controller tests 不被当作安全证据。

**命令**：

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=FileQueryControllerWebMvcTest,PlatformReadControllerWebMvcTest,EvaluationItemQueryControllerWebMvcTest,FileQueryApplicationServiceTest,PublicAttachmentQueryRepositoryIntegrationTest,MinimalEReadSecurityIntegrationTest,FileUploadControllerWebMvcTest,PlatformRuleConfigProviderTest,TeamDeliverySqlConsistencyTest test
```

**验收**（刚性量化指标）：
- Maven 输出 `BUILD SUCCESS`。
- 所有指定测试类全部 PASS，0 failures，0 errors，0 skipped。
- `MinimalEReadSecurityIntegrationTest` 不使用 `@AutoConfigureMockMvc(addFilters = false)`。

**Files:**
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/platform/MinimalEReadSecurityIntegrationTest.java`
- Modify as needed: tests created in Milestones 2-4

- [ ] **Step 1: Write security integration test**

Use the pattern from `SecurityProbeControllerWebMvcTest`: import `SecurityConfiguration` and JWT filter stack, mock application services, and issue a valid JWT with no E-admin authorities.

Test matrix:
- no token on one platform endpoint returns 401;
- valid token without admin authorities on platform status returns 200;
- valid token without admin authorities on evaluation items returns 200;
- valid token without admin authorities on file metadata returns 200 when service allows it;
- valid token without admin authorities on public attachments returns 200.

- [ ] **Step 2: Run security test to verify failure or pass**

Run:

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=MinimalEReadSecurityIntegrationTest test
```

Expected after endpoints exist: `BUILD SUCCESS`, 0 skipped.

- [ ] **Step 3: Run focused regression**

Run the full Milestone 5 command.

Expected: `BUILD SUCCESS`, 0 skipped.

## 具体步骤

Execution from repository root:

```bash
git status --short
```

Expected before implementation: only the approved spec and proposal plan changes are present, or any unrelated user changes are identified and left untouched.

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PlatformRuleConfigProviderTest,TeamDeliverySqlConsistencyTest test
```

Expected at Milestone 1 completion: `BUILD SUCCESS`.

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PlatformReadControllerWebMvcTest,EvaluationItemQueryControllerWebMvcTest,EvaluationItemQueryApplicationServiceTest test
```

Expected at Milestone 2 completion: `BUILD SUCCESS`.

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=FileUploadControllerWebMvcTest,FileUploadApplicationServiceTest,MybatisFileAssetRegistryIntegrationTest test
```

Expected at Milestone 3 completion: `BUILD SUCCESS`.

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=FileQueryControllerWebMvcTest,FileQueryApplicationServiceTest,PublicAttachmentQueryRepositoryIntegrationTest test
```

Expected at Milestone 4 completion: `BUILD SUCCESS`.

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=FileQueryControllerWebMvcTest,PlatformReadControllerWebMvcTest,EvaluationItemQueryControllerWebMvcTest,FileQueryApplicationServiceTest,PublicAttachmentQueryRepositoryIntegrationTest,MinimalEReadSecurityIntegrationTest,FileUploadControllerWebMvcTest,PlatformRuleConfigProviderTest,TeamDeliverySqlConsistencyTest test
```

Expected before final report: `BUILD SUCCESS`, 0 skipped.

## 验证与验收

- [ ] SQL safety：`rg -n "DROP TABLE" docs/team-delivery/group-e-platform-governance-attachment-ai.safe-init.sql` returns no output.
- [ ] Config materialization：`PlatformRuleConfigProviderTest`全部 PASS，deadline 字段正确反序列化。
- [ ] Platform reads：`PlatformReadControllerWebMvcTest`全部 PASS，status/deadline 返回 `source=NACOS`。
- [ ] Evaluation item reads：`EvaluationItemQueryApplicationServiceTest`全部 PASS，enabled-only 和 stable sort 均被断言。
- [ ] Upload safe response：`FileUploadControllerWebMvcTest`全部 PASS，storage location fields 不存在。
- [ ] File reads：`FileQueryApplicationServiceTest`全部 PASS，owner/public/private/inactive/missing URL 分支均覆盖。
- [ ] Public attachment repository：`PublicAttachmentQueryRepositoryIntegrationTest`全部 PASS，join/filter/order 均覆盖。
- [ ] Security：`MinimalEReadSecurityIntegrationTest`全部 PASS，filters enabled，anonymous rejected，authenticated non-admin allowed。
- [ ] Focused regression：Milestone 5 Maven 命令 `BUILD SUCCESS`，0 skipped。

## 文档更新

Required:
- `docs/superpowers/specs/2026-07-06-minimal-e-foundation-design.md` — 已在计划前修订，补充 response 字段来源和 safe-init seed `updated_at` 要求。
- `docs/team-delivery/group-e-platform-governance-attachment-ai.safe-init.sql` — 新增运行时安全初始化 SQL。

Evaluated, no change in this implementation round:
- `docs/team-delivery/group-e-platform-governance-attachment-ai.md` — target-state E 文档保持不变，Minimal E 的 narrowed scope 已在 spec 中说明。
- `docs/team-delivery/group-b-student-application.md` — B 组后续 spec 再写联调契约。

## 幂等性与恢复

- Safe-init SQL must be rerunnable: `CREATE TABLE IF NOT EXISTS` and guarded seed inserts mean rerun does not delete or duplicate runtime file rows.
- H2 integration tests may drop test tables inside in-memory databases only; do not introduce drop statements into runtime safe-init SQL.
- If Milestone 3 upload response change breaks existing tests, update only tests that assert external response shape; do not remove internal storage fields from `StoredFileDescriptor` or persistence objects.
- If security integration test fails with 401 for valid token, debug JWT/session mock setup before weakening endpoint security annotations.
- If Maven fails because unrelated tests outside the focused suite fail, capture the exact failure and keep this plan scoped to Minimal E before broadening changes.

## 产物与备注

Spec path:

```text
docs/superpowers/specs/2026-07-06-minimal-e-foundation-design.md
```

Plan path:

```text
docs/superpowers/plans/active/2026-07-06/2026-07-06-minimal-e-foundation.md
```

Current known git state before implementation:

```text
?? docs/superpowers/specs/2026-07-06-minimal-e-foundation-design.md
?? docs/superpowers/plans/active/2026-07-06/2026-07-06-minimal-e-foundation.md
?? docs/superpowers/sdd/runs/2026-07-06-minimal-e-foundation-state.md
```

## 接口与依赖

Application service interfaces to add:

```java
public class PlatformReadApplicationService {
    public PlatformMenuStatus getMenuStatus();
    public PlatformMenuDeadline getMenuDeadline();
    public List<EvaluationItemResponse> listEvaluationItems(String categoryCode);
}
```

```java
public interface FileQueryRepository {
    Optional<FileAssetDO> findActiveFileByFileId(String fileId);
    boolean existsPublishedAllPublicAttachment(String fileId);
    List<PublicAttachmentDescriptor> listPublishedAllPublicAttachments(String categoryCode);
}
```

```java
public class FileQueryApplicationService {
    public FileMetadataResponse getMetadata(String fileId);
    public FileAccessUrlResponse getAccessUrl(String fileId);
    public List<PublicAttachmentResponse> listPublicAttachments(String categoryCode);
}
```

`PublicAttachmentDescriptor` is an application-layer query model returned by the repository. It keeps MyBatis join results separate from the B-facing `PublicAttachmentResponse` assembled by `FileQueryApplicationService`.

Common error mapping must use existing codes:
- no file visibility: `CommonErrorCode.ACCESS_DENIED` / `AUTH-4030`
- missing or inactive file: `CommonErrorCode.RESOURCE_NOT_FOUND` / `RES-4040`
- config missing: `CommonErrorCode.NACOS_CONFIG_LOAD_FAILED` / `CFG-5031`
- storage URL generation failure: `CommonErrorCode.FILE_STORAGE_FAILED` / `EXT-5033`

## 后续修复记录（Phase 6）

No Phase 6 feedback yet.

---

[2026-07-06 17:47:36+08:00] 修改说明：创建 Minimal E Foundation 执行计划草案，覆盖 spec 修订后进入实现前的 proposal review gate；理由是用户要求优化 spec 后进入执行计划阶段，且按计划流程需先 review 再实现。

---

[2026-07-06 18:02:27+08:00] 修改说明：根据执行计划审核结果修订 Milestone 1 和 Milestone 4。Milestone 1 增加 safe-init SQL 重复执行不覆盖 runtime `file_asset` 的集成验证；Milestone 4 改为先定义 DTO、repository contract 和 service 签名，再写失败测试，并新增 `PublicAttachmentDescriptor` 避免 repository 直接返回 B-facing response。理由是提升计划可执行性、验证 SQL 幂等性，并保持 application/infra 边界清晰。

---

[2026-07-06 18:06:54+08:00] 修改说明：继续修订 Milestone 4 Step 1，将其扩展为 query contracts and skeletons，要求先创建 `MybatisFileQueryRepository` 和 `FileQueryController` 最小骨架。理由是确保 Step 5 的预期失败来自未实现行为，而不是缺少 repository/controller 类导致的编译失败。

---

[2026-07-06 19:19:10+08:00] 修改说明：修订 Milestone 1 的 safe-init SQL 约束，要求 SQL 文件使用 MySQL 与 H2 MySQL-mode 都能执行的 DDL/seed 子集，并要求幂等测试执行实际 safe-init 文件而非测试内手写等价 DDL。理由是让幂等验证覆盖真实初始化产物，避免测试失败在 MySQL 方言兼容性而不是 schema 安全性。
