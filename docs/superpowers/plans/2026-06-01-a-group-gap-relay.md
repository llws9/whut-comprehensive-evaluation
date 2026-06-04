# A组缺口接力文档（Context Reset 后继续）

## 1. 目标
在清空上下文后，继续完成 A 组剩余接口缺口，并严格按 TDD（红->绿->重构）推进。

## 2. 当前结论（已复盘）
对照 `docs/team-delivery/group-a-identity-user-admin.md` 的 A-1~A-23：

### 2.1 已完成（代码路由已存在）
- A-1/A-2/A-19: `whut-eval-app/src/main/java/edu/whut/eval/app/security/AuthController.java:62,92,136`
- A-3: `whut-eval-app/src/main/java/edu/whut/eval/app/security/SecurityProbeController.java:23`
- A-4: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserIdentityQueryController.java:23`
- A-5/A-6/A-7: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserAdminController.java:42,60,80`
- A-9: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/RoleAdminController.java:28`
- A-13/A-14/A-15/A-16/A-17/A-18: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/IamRoleAssignmentAdminController.java:54,72,88,130,104,111`
- A-20/A-21: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminQueryController.java:85,96`
- A-22/A-23: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserMembershipAdminController.java:35,44`

### 2.2 未完成缺口（下一阶段）
- **A-8** `POST /api/admin/users/import`
- **A-10** `POST /api/admin/roles`
- **A-11** `PATCH /api/admin/roles/{roleId}`
- **A-12** `POST /api/admin/roles/{roleId}/permissions`

## 3. 推荐接力顺序（按风险和依赖）
1. A-10 角色创建
2. A-11 角色修改
3. A-12 角色绑定权限
4. A-8 用户批量导入

> 说明：A-10/11/12 同属角色管理域，先闭环可减少来回改动；A-8 往往涉及文件解析与批处理，复杂度更高，放后。

## 4. 严格 TDD 执行模板（每个子任务都重复）
1. 先写失败测试（WebMvc/Service/Repository 至少覆盖主链路）。
2. 单独运行该测试并确认 **确实失败**（失败原因必须是“功能未实现”，不是测试写错）。
3. 写最小实现代码让测试通过。
4. 回跑同一组测试，确认全绿。
5. 必要时小步重构并保持全绿。
6. 一个子任务一个 commit（保持可回滚和可审阅）。

## 5. 下一次会话建议起手命令
在仓库根目录执行（或让 Claude 执行）：

```bash
mvn -pl whut-eval-app -Dtest=RoleAdminQueryControllerWebMvcTest,DefaultRoleAdminQueryApplicationServiceTest,MybatisPlusRoleAdminQueryRepositoryTest test
```

然后进入 A-10 的红绿循环：
- 新增/修改 `RoleAdmin` 相关 WebMvc 测试（先红）
- 补 `POST /api/admin/roles`
- 绿后提交

## 6. 验收口径
- 每个缺口都要有：
  - 路由存在
  - 鉴权注解正确
  - 至少一条主流程测试
  - 至少一条失败/冲突场景测试（如重复、不存在、状态冲突）
- 最终回归覆盖 A-8/A-10/A-11/A-12 相关测试全绿。

## 7. 备注
当前工作树状态显示仅有计划文档变更，请在新会话先确认 `git status` 再继续实现。