# Role Admin Task4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以现有 `iam_role` 冻结 schema 为边界，补齐 `GET /api/admin/roles`、`POST /api/admin/roles`、`PATCH /api/admin/roles/{roleId}` 三条角色模板管理链路。

**Architecture:** 延续现有分层模式，接口层负责请求映射和返回 DTO，application 层负责字段收敛、唯一性/枚举合法性/冲突语义，repository 层负责 `iam_role` 与 `iam_role_permission` 的分页聚合和持久化。严格按 TDD：先写 WebMvc、service、repository 失败测试，再补最小实现。

**Tech Stack:** Java 21, Spring Boot, Spring MVC, Spring Security `@PreAuthorize`, MyBatis-Plus, JUnit 5, Mockito, H2 integration test

---

### Task 1: 定义角色管理契约

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/RoleAdminPageQuery.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/RoleAdminPageItemView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/RoleAdminView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/CreateRoleCommand.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/command/UpdateRoleCommand.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAdminQueryApplicationService.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAdminCommandApplicationService.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/model/IamRoleAdminPageItem.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/model/IamRole.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/query/RoleAdminPageQuery.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/RoleAdminQueryRepository.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/RoleAdminCommandRepository.java`

- [ ] Step 1: 写最小失败测试，约束 application/domain 类型名和字段
- [ ] Step 2: 运行定向测试确认缺类失败
- [ ] Step 3: 新增最小 record/interface 定义，仅包含 `roleId/roleCode/roleName/roleScope/status/permissionCount/createdAt`
- [ ] Step 4: 运行编译相关测试确认类型层通过

### Task 2: 先做接口层失败测试

**Files:**
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/RoleAdminController.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/CreateRoleRequest.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/UpdateRoleRequest.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/response/RoleAdminPageItemResponse.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/response/RoleAdminResponse.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminControllerWebMvcTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminControllerSecurityAnnotationTest.java`

- [ ] Step 1: 写分页、创建、修改、400 参数校验、权限注解的失败测试
- [ ] Step 2: 运行 `RoleAdminControllerWebMvcTest` 和安全注解测试，确认因类/方法缺失失败
- [ ] Step 3: 实现 controller/request/response 与 `role.manage` 权限常量
- [ ] Step 4: 重跑接口层测试直到变绿

### Task 3: 实现 application 层业务校验

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/DefaultRoleAdminQueryApplicationService.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/DefaultRoleAdminCommandApplicationService.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/DefaultRoleAdminQueryApplicationServiceTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/DefaultRoleAdminCommandApplicationServiceTest.java`

- [ ] Step 1: 写失败测试，覆盖 `pageNo/pageSize/status/roleScope` 合法性、`roleCode` 唯一、角色不存在、no-op/并发冲突
- [ ] Step 2: 运行 service 测试确认按预期失败
- [ ] Step 3: 实现最小业务逻辑，创建仅接受 `roleCode/roleName/roleScope/status`，修改仅允许 `roleName/roleScope/status`
- [ ] Step 4: 重跑 service 测试直到变绿

### Task 4: 实现 repository 聚合与持久化

**Files:**
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/entity/IamRoleDO.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/entity/IamRolePermissionDO.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/IamRolePermissionMapper.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusRoleAdminQueryRepository.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusRoleAdminCommandRepository.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/infra/MybatisPlusRoleAdminQueryRepositoryTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/MybatisPlusRoleAdminCommandRepositoryIntegrationTest.java`

- [ ] Step 1: 写失败测试，覆盖 `permissionCount` 聚合、`roleCode` 查重、乐观更新语义
- [ ] Step 2: 运行 repository 测试确认失败
- [ ] Step 3: 补 `role_scope/created_at` 映射、实现分页查询和 create/update repository
- [ ] Step 4: 重跑 repository 测试直到变绿

### Task 5: 回归、任务清单与诊断

**Files:**
- Modify: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.trae/specs/complete-a-group-phase3-user-role-admin/tasks.md`

- [ ] Step 1: 运行 Task4 相关定向测试
- [ ] Step 2: 运行 `mvn -pl whut-eval-app -am test` 的最小回归子集
- [ ] Step 3: 用诊断工具检查最近编辑文件
- [ ] Step 4: 勾选根目录 `tasks.md` 的 Task4
