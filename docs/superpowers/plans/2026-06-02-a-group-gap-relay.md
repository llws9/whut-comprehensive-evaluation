# A组缺口接力文档（Context Reset 后继续）

## 1. 当前目标
在清空上下文后继续推进 A 组缺口，当前进入 **A-11：`PATCH /api/admin/roles/{roleId}`**。

---

## 2. 已完成状态（本轮）

### 2.1 A-10 已完成（角色创建）
已完成 `POST /api/admin/roles` 最小闭环，包含：
- Controller 路由与鉴权
- 应用服务规则（必填校验、重复 roleCode 冲突）
- 写仓储实现
- WebMvc + Service + Repository 测试

已落地提交：
1. `e1b9c72` feat: 新增管理端角色创建接口骨架
2. `4cd4116` test: 增加角色创建应用服务规则测试
3. `5e05d16` feat: 补齐角色创建写仓储实现与测试

关键文件：
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/RoleAdminController.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAdminApplicationService.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamRoleCommandRepository.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminQueryControllerWebMvcTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminApplicationServiceTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/infra/MybatisPlusIamRoleCommandRepositoryTest.java`

回归结果（已通过）：
- `RoleAdminQueryControllerWebMvcTest`
- `RoleAdminApplicationServiceTest`
- `MybatisPlusIamRoleCommandRepositoryTest`
- `DefaultRoleAdminQueryApplicationServiceTest`
- `MybatisPlusIamRoleQueryRepositoryTest`

---

## 3. 下一任务（已确认范围）

### 3.1 A-11 范围确认
接口：`PATCH /api/admin/roles/{roleId}`

仅支持更新字段：
- `roleName`
- `status`

**不在本轮范围：**
- `roleScope` 更新
- 其他字段扩展

---

## 4. 推荐继续顺序（严格 TDD）
1. A-11 设计确认（错误语义 + 验收口径）
2. A-11 RED：先写失败测试（主链路 + 冲突/非法状态）
3. A-11 GREEN：最小实现
4. A-11 回归与小步重构
5. 再进入 A-12

---

## 5. 下一次会话起手命令
在仓库根目录执行：

```bash
git status
git log --oneline -8
mvn -pl whut-eval-app -am -Dtest=RoleAdminQueryControllerWebMvcTest,RoleAdminApplicationServiceTest,MybatisPlusIamRoleCommandRepositoryTest,DefaultRoleAdminQueryApplicationServiceTest,MybatisPlusIamRoleQueryRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

若全绿，直接进入 A-11 的红绿循环。

---

## 6. A-11 验收口径（提前对齐）
- 路由存在：`PATCH /api/admin/roles/{roleId}`
- 鉴权注解正确（角色管理权限）
- 至少 1 条主链路测试（更新成功）
- 至少 1 条失败场景测试（如非法状态/资源不存在/冲突）
- 相关角色域回归测试保持全绿

---

## 7. 当前工作上下文
- 代码在 worktree：
  `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a10-role-create`
- 分支：`feat/a10-role-create`
- 下一步可直接在该分支继续 A-11。
