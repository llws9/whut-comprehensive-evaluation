# A-19 Session Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地 `iam_session` 的最小可编译基础设施，包括 DDL 对齐、领域模型、仓储契约与 MyBatis-Plus 持久化骨架，并同步更新 A-19 `tasks.md` 已完成项。

**Architecture:** 仅实现 `Task 1` 的基础层，不提前接入登录、refresh、filter、logout 主链路。领域层定义 `IamSession` 与状态行为，infra 层用 `IamSessionDO + IamSessionMapper + MybatisPlusIamSessionRepository` 对齐现有 `iam_session` 表，并将 `token_id` 明确映射为 `sessionId`。

**Tech Stack:** Java 21, Spring Boot, MyBatis-Plus, JUnit 5, Maven

---

### Task 1: 对齐 `iam_session` DDL 与领域契约

**Files:**
- Modify: `docs/team-delivery/group-a-identity-user-admin.sql`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/model/IamSession.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/model/IamSessionStatus.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/IamSessionRepository.java`

- [ ] Step 1: 先写最小仓储/模型存在性测试或编译约束
- [ ] Step 2: 运行定向测试或 `compile`，确认当前因缺少 `IamSession` 相关类型失败
- [ ] Step 3: 新增 `IamSession`、`IamSessionStatus`、`IamSessionRepository`，实现 `isActive/revoke/extendTo` 最小行为
- [ ] Step 4: 更新 `group-a-identity-user-admin.sql` 中 `iam_session` 注释，显式声明 `token_id` 语义为 `sessionId`

### Task 2: 落地持久化实体与仓储骨架

**Files:**
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/entity/IamSessionDO.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/IamSessionMapper.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamSessionRepository.java`

- [ ] Step 1: 写失败测试或编译约束，锁定 `sessionId -> token_id`、状态、时间字段映射
- [ ] Step 2: 运行定向测试或 `compile`，确认缺类失败
- [ ] Step 3: 新增 `IamSessionDO` 和 `IamSessionMapper`
- [ ] Step 4: 新增 `MybatisPlusIamSessionRepository`，提供 `save/findBySessionId/revoke/extendExpiration` 最小骨架实现

### Task 3: 验证与任务回写

**Files:**
- Modify: `.trae/specs/implement-a19-logout-session/tasks.md`

- [ ] Step 1: 运行 `mvn -q -pl whut-eval-app -am test -Dtest=none -DfailIfNoTests=false` 或等价 `compile` 验证基础骨架可编译
- [ ] Step 2: 运行诊断检查新增/修改文件
- [ ] Step 3: 将 `Task 1` 下已完成的子项回写到 `.trae/specs/implement-a19-logout-session/tasks.md`
