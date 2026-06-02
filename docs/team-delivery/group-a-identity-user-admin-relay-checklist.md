# A组任务接力清单（身份认证与用户权限）

> 用途：用于多人接力推进 A 组剩余收口工作。
> 来源：基于当前代码状态对 `group-a-identity-user-admin.md` 的差距分析。
> 更新时间：2026-06-02

---

## 一、接力规则

- 建议按 **P0 → P1 → P2** 顺序推进。
- 每完成一项，更新本文件的状态与“完成说明”。
- 每项至少包含：
  - 代码改动文件
  - 最小验证结果（接口/测试）
  - 是否影响文档契约

状态约定：
- [ ] 未开始
- [~] 进行中
- [x] 已完成

---

## 二、P0（主链路优先）

### 1. A-5 分页查询用户改为真实查库
- **状态**：[x]
- **目标**：`/api/admin/users` 返回真实分页结果，支持 `pageNo/pageSize/keyword/status/orgUnitId`。
- **当前问题**：应用层仍为占位实现，固定空页。
- **关键文件**：
  - `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserAdminController.java`
  - `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`
  - `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/UserAdminPageQuery.java`
  - `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/query/UserPageQuery.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamUserQueryRepository.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerWebMvcTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminApplicationServiceTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/iam/MybatisPlusIamUserQueryRepositoryIntegrationTest.java`
- **验收标准**：
  - 不再固定空列表
  - `keyword` 支持 `userNo/userName` 模糊查询
  - `status/orgUnitId` 过滤生效
- **完成说明**：
  - `/api/admin/users` 入参切换为 `keyword`（严格模式，不再接收 `userName`）
  - `UserAdminApplicationService#pageUsers` 改为真实查库并返回分页结果（移除占位空页实现）
  - 仓储层支持 `keyword(userNo or userName) + status + orgUnitId` 组合过滤
  - 最小验证：`mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=UserAdminControllerWebMvcTest,UserAdminApplicationServiceTest,MybatisPlusIamUserQueryRepositoryIntegrationTest test`
  - 文档契约影响：A-5 参数口径与组文档保持一致（`keyword`）

### 2. A-8 批量导入用户改为真实导入
- **状态**：[x]
- **目标**：`/api/admin/users/import` 完成 Excel 解析及导入。
- **当前问题**：仅参数校验，返回固定 `0/0/0`。
- **关键文件**：
  - `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`
  - `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserImportParser.java`
  - `whut-eval-application/src/main/java/edu/whut/eval/application/iam/query/UserImportRowView.java`
  - `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/IamUserCommandRepository.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/iam/ExcelUserImportParser.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/IamUserMapper.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusIamUserCommandRepository.java`
  - `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserAdminController.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminApplicationServiceTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminControllerWebMvcTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/iam/ExcelUserImportParserTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/iam/MybatisPlusIamUserCommandRepositoryIntegrationTest.java`
- **验收标准**：
  - `totalCount/successCount/failedCount` 真实统计
  - `failedRows` 包含行号与原因
  - `INSERT_ONLY` 遇重复按契约返回冲突语义
- **完成说明**：
  - 导入链路从固定 `0/0/0` 改为真实解析 + 行级校验 + 落库统计
  - `failedRows` 返回真实 `rowNo + reason`
  - `INSERT_ONLY` 模式检测到重复 `userNo` 时返回 409（`BIZ-4090`）并整批回滚
  - 解析模板沿用现有约定表头：`userNo/userName/password/email/phone`
  - 最小验证：`mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=UserAdminApplicationServiceTest,UserAdminControllerWebMvcTest,ExcelUserImportParserTest,MybatisPlusIamUserCommandRepositoryIntegrationTest test`

---

## 三、P1（契约一致性）

### 3. A-6 创建用户补齐 primaryOrgUnitId 落库
- **状态**：[x]
- **目标**：创建用户时若传 `primaryOrgUnitId`，同步写入 `org_membership` 主组织归属。
- **当前问题**：字段已接收，业务未使用。
- **关键文件**：
  - `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/CreateUserRequest.java`
  - `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`
- **验收标准**：
  - 创建后 `A-22` 可查到对应主组织
  - 非法组织 ID 返回 404 语义
- **完成说明**：
  - 创建用户时若传 `primaryOrgUnitId`，同事务写入 `org_membership` 主组织记录
  - 非法组织 ID 抛 `ResourceNotFoundException`，接口返回 404（RES-4040）
  - 最小验证：UserAdminApplicationServiceTest / UserAdminControllerWebMvcTest / MybatisPlusUserMembershipAdminRepositoryIntegrationTest

### 4. A-4 身份查询补鉴权（user.manage）
- **状态**：[ ]
- **目标**：`/api/iam/users/{userNo}/identity` 增加 `user.manage` 鉴权。
- **当前问题**：控制器缺少 `@PreAuthorize`。
- **关键文件**：
  - `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserIdentityQueryController.java`
- **验收标准**：
  - 无权限调用返回 403
  - 有权限调用保持正常
- **完成说明**：
  - （待填写）

---

## 四、P2（角色契约收口）

### 5. A-10 创建角色支持 roleScope
- **状态**：[ ]
- **目标**：请求显式传 `roleScope` 并校验，不再硬编码。
- **当前问题**：当前实现固定写死 `ORG_SUBTREE`。
- **关键文件**：
  - `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/CreateRoleRequest.java`
  - `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAdminApplicationService.java`
- **验收标准**：
  - 合法 `roleScope` 可持久化
  - 非法值返回 400
- **完成说明**：
  - （待填写）

### 6. A-11 修改角色补快照并发校验
- **状态**：[ ]
- **目标**：更新时携带快照字段，冲突返回 409。
- **当前问题**：无快照并发校验，字段口径与文档不一致。
- **关键文件**：
  - `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/request/UpdateRoleRequest.java`
  - `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAdminApplicationService.java`
- **验收标准**：
  - 并发修改可触发冲突检测
  - 冲突时返回约定错误码/状态
- **完成说明**：
  - （待填写）

### 7. A-12 replaceAll 语义收口
- **状态**：[ ]
- **目标**：`replaceAll` 参数语义与实现一致。
- **当前问题**：当前实际始终走整集合替换。
- **可选方案**：
  - 方案A：明确只支持整集合替换（固定 true，简化契约）
  - 方案B：实现 `replaceAll=false` 的增量绑定语义
- **关键文件**：
  - `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/RoleAdminController.java`
  - `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/RoleAdminApplicationService.java`
- **验收标准**：
  - 接口文档、参数、实现三者一致
- **完成说明**：
  - （待填写）

---

## 五、文档同步项

### 8. 更新组文档进度描述
- **状态**：[ ]
- **目标**：修正 `group-a-identity-user-admin.md` 中过时进度描述。
- **建议更新**：
  - 删除“`A-9~A-12` HTTP 入口未落地”相关表述
  - 改为“入口已落地，仍有契约细节待收口（roleScope/并发快照/replaceAll）”
- **关键文件**：
  - `docs/team-delivery/group-a-identity-user-admin.md`
- **完成说明**：
  - （待填写）

---

## 六、建议接力顺序

1. 先完成 P0 的 1、2（保证联调可用）
2. 再做 P1 的 3、4（补齐契约一致性）
3. 最后做 P2 的 5、6、7（角色链路收口）
4. 完成后统一更新第 8 项文档说明

---

## 七、接力记录

| 日期 | 处理人 | 完成项 | 备注 |
|---|---|---|---|
| 2026-06-02 | Claude | 初始化清单 | 首版建立 |
| 2026-06-02 | Claude | A-5 | /api/admin/users 改为真实查库，支持 keyword/status/orgUnitId |
| 2026-06-02 | Claude | A-8 | /api/admin/users/import 真实导入，INSERT_ONLY 重复返回 409 并整批回滚 |
| 2026-06-03 | Claude | A-6 | 创建用户补 primaryOrgUnitId 落库与 404 语义 |
