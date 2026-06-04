# A Group SDD Gap Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox syntax for tracking; all planned steps are now checked as completed.

**Goal:** 按 A 组交付缺口优先级逐个闭环：先恢复测试基线，再补齐 A-10/A-11/A-12，随后闭合 session/token 生命周期，最后统一 SQL 与权限初始化。

**Architecture:** 保持现有多模块分层：`interfaces` 暴露 HTTP 契约，`application` 编排业务用例，`domain` 定义模型和仓储契约，`infra` 实现 MyBatis 持久化。每个任务必须先有失败测试或现有失败证据，再做最小实现，并把验证结果写入 state。

**Tech Stack:** Java 21, Spring Boot, Spring Security, MyBatis-Plus, JUnit 5, MockMvc, Maven, MySQL 8 SQL

---

## Task Matrix

### Task T0: 恢复 A 组测试编译基线
**Files:**
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminApplicationServiceTest.java`
- Modify if needed: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`

- [x] Step 1: 运行 `mvn -q -pl whut-eval-app -am test` 记录当前 `testCompile` 失败。
- [x] Step 2: 修正已迁移到 `domain.org` 的导入和测试桩，避免测试引用不存在的 `domain.iam` 类型。
- [x] Step 3: 运行 `mvn -q -pl whut-eval-app -am -Dtest=UserAdminApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [x] Step 4: 运行 `mvn -q -pl whut-eval-app -am test`，记录剩余失败。

### Task T1: 补齐 A-10/A-11/A-12 角色模板写接口
**Files:**
- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/RoleAdminController.java`
- Create/modify: role admin request/response DTOs
- Create/modify: role admin application service and command repository
- Add/modify tests under `whut-eval-app/src/test/java/edu/whut/eval/app/iam` and `whut-eval-app/src/test/java/edu/whut/eval/app/infra`

- [x] Step 1: 先写/恢复 A-10/A-11/A-12 WebMvc、service、repository 失败测试。
- [x] Step 2: 最小实现 `POST /api/admin/roles`。
- [x] Step 3: 最小实现 `PATCH /api/admin/roles/{roleId}`，包含快照冲突。
- [x] Step 4: 最小实现 `POST /api/admin/roles/{roleId}/permissions`，整集合替换且允许空集合。
- [x] Step 5: 运行角色模板定向测试和 A 组回归。

### Task T2: 闭合 login/refresh/logout session 生命周期
**Files:**
- Modify: `whut-eval-app/src/main/java/edu/whut/eval/app/security/AuthController.java`
- Modify: JWT/session application and infra classes
- Add/modify tests under `whut-eval-app/src/test/java/edu/whut/eval/app/security`

- [x] Step 1: 先写 session 创建、sid claim、refresh session 校验、logout 无效 session 返回 401 的失败测试。
- [x] Step 2: 登录创建 `iam_session` 并签发带 `sid` 的 access/refresh token。
- [x] Step 3: refresh 校验当前 session 并续期同一 session。
- [x] Step 4: 受保护请求校验 session 有效性。
- [x] Step 5: logout 撤销当前 session，已失效时返回 `AUTH-4012`。

### Task T3: 统一 A 组 SQL 与权限码初始化
**Files:**
- Modify: `docs/team-delivery/group-a-identity-user-admin.sql`
- Modify if needed: `whut-eval-app/src/main/resources/sql/iam/*.sql`
- Modify docs/checklist if needed

- [x] Step 1: 增加或运行 SQL 可执行性验证，复现 `iam_session` 字段不匹配。
- [x] Step 2: 修正 `iam_session` DDL/初始化字段口径。
- [x] Step 3: 以 `AuthorizationPermissionCodes.java` 为准更新 `iam_permission` 初始化。
- [x] Step 4: 同步角色权限和范围规则中的 `permission_code`。
- [x] Step 5: 记录 `evaluation_category/evaluation_item` 跨组依赖边界。


---

## Completion Record

- **Status:** completed
- **Branch/worktree:** `chore/a-group-sdd-gap-closure` @ `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-sdd-gap-closure`
- **Commit:** `72e9803 feat(iam): close group a auth and role admin gaps`
- **Completed scope:** T0/T1/T2/T3 plus post-review fixes.
- **Final verification:** `mvn -q -pl whut-eval-app -am test` exited 0; VS Code diagnostics returned `[]`.
- **Review fixes:** `JwtAuthenticationFilter` now reads the configurable `JwtProperties.sessionIdClaim`; A group SQL seed keeps `ACTIVE` sessions unexpired and preserves explicit `EXPIRED`/`REVOKED` examples.
- **Outcome:** A-10/A-11/A-12 role write APIs are implemented; login/refresh/protected/logout session lifecycle is closed; SQL permission/session seed is aligned with application constants and T2 session semantics.
