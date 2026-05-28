# A组第三阶段用户与角色后台闭环 Spec

## Why
前两阶段已经补齐了角色分配管理与权限/组织元数据接口，但管理端仍缺少用户后台与角色模板后台主体能力，导致管理员无法在新链路中独立完成账号管理、角色模板维护和权限绑定。将 `A-5~A-12` 单独收口，可以先闭合后台主流程，避免与 `A-19 logout` 的会话模型耦合。

## What Changes
- 新增 `A-5~A-8`：用户分页查询、创建用户、修改用户状态、批量导入用户。
- 新增 `A-9~A-12`：角色模板分页查询、创建角色模板、修改角色模板、绑定角色权限。
- 补齐管理端鉴权常量与接口鉴权：`user.manage`、`user.import`、`role.manage`。
- 复用现有 `iam` admin 分层风格，新增必要的 request/response DTO、application service、repository/mapper 与定向测试。
- `A-8` 第一轮按“最小可用导入”交付：冻结 `multipart/form-data` 契约、模板/参数校验、结果摘要模型和冲突语义；不把复杂 Excel 编排扩展成新的子项目。
- `A-9~A-11` 按现有 `iam_role` schema 收敛：`role_scope` 作为真实持久字段，`description` 不假设有独立列。

## Impact
- Affected specs: `A-5`、`A-6`、`A-7`、`A-8`、`A-9`、`A-10`、`A-11`、`A-12`
- Affected code: `whut-eval-interfaces`、`whut-eval-application`、`whut-eval-domain`、`whut-eval-infra`、`whut-eval-app`、`docs/reference/api-surface.md`

## ADDED Requirements
### Requirement: User Administration Query
系统 SHALL 提供管理端用户分页查询接口，支持按关键字、状态和组织过滤，并返回管理端所需的组织与角色摘要字段。

#### Scenario: Query users by filters
- **WHEN** 管理员调用 `GET /api/admin/users` 并携带 `pageNo/pageSize/keyword/status/orgUnitId`
- **THEN** 系统返回 `ApiResponse<PageResult<UserView>>`
- **AND** `UserView` 至少包含 `userId/userNo/userName/status/orgUnits/roleCodes/createdAt`
- **AND** 当 `orgUnitId` 不存在时返回 `404 RES-4040`

### Requirement: User Administration Mutation
系统 SHALL 提供创建用户与修改用户状态接口，并对重复编号、非法状态和无效组织做显式校验。

#### Scenario: Create user successfully
- **WHEN** 管理员调用 `POST /api/admin/users` 且 `userNo` 唯一、`primaryOrgUnitId` 合法
- **THEN** 系统创建用户并返回 `userId/userNo/userName/status`
- **AND** 若指定主组织，则同步建立主归属关系

#### Scenario: Update user status successfully
- **WHEN** 管理员调用 `PATCH /api/admin/users/{userId}/status` 且目标状态为 `ACTIVE`、`DISABLED` 或 `LOCKED`
- **THEN** 系统更新用户状态并返回 `ApiResponse.success(null)`
- **AND** 当状态未变化或资源状态冲突时返回 `409 BIZ-4090`

### Requirement: User Import
系统 SHALL 提供最小可用的用户批量导入接口，支持模板校验、导入模式校验与结果摘要返回。

#### Scenario: Import users with summary result
- **WHEN** 管理员调用 `POST /api/admin/users/import` 上传有效文件
- **THEN** 系统返回 `totalCount/successCount/failedCount/failedRows`
- **AND** 当 `importMode=INSERT_ONLY` 且遇到重复编号时返回 `409 BIZ-4090`
- **AND** 当文件为空、模板错误或 `importMode` 非法时返回 `400 VAL-4001`

### Requirement: Role Template Management
系统 SHALL 提供角色模板分页查询、创建与修改能力，并保持角色编码唯一；其中角色范围以 `role_scope` 作为真实持久字段。

#### Scenario: Query role templates
- **WHEN** 管理员调用 `GET /api/admin/roles`
- **THEN** 系统返回包含 `roleId/roleCode/roleName/status/permissionCount/createdAt` 的分页结果
- **AND** 若返回 `description`，其语义必须与现有 schema 可稳定推出的字段一致，不得伪装成独立持久列

#### Scenario: Create or update role template
- **WHEN** 管理员调用 `POST /api/admin/roles` 或 `PATCH /api/admin/roles/{roleId}`
- **THEN** 系统校验 `roleCode` 唯一、`roleScope`/`status` 合法
- **AND** 创建接口返回 `roleId/roleCode/roleName/status`
- **AND** 修改接口仅允许变更 `roleName/roleScope/status`

#### Scenario: Keep role schema aligned
- **WHEN** 本轮实现 `A-9~A-11`
- **THEN** 系统不得引入未在当前冻结 schema 中持久化的 `roleType` 或独立 `description` 列依赖
- **AND** 所有 request/response、测试与仓储语义都需与现有 `iam_role(role_scope, status, created_at)` 一致

### Requirement: Role Permission Binding
系统 SHALL 提供角色权限整集合绑定接口，并以 `A-20` 权限字典作为唯一权限码来源。

#### Scenario: Replace role permissions
- **WHEN** 管理员调用 `POST /api/admin/roles/{roleId}/permissions` 并提交 `permissionCodes`
- **THEN** 系统按 `replaceAll=true` 语义替换角色权限集合
- **AND** 若任一 `permissionCode` 不存在则返回 `404 RES-4040`
- **AND** 若权限集合为空或包含空值则返回 `400 VAL-4001`

## MODIFIED Requirements
### Requirement: Management Permission Constants
系统 SHALL 统一维护管理端 canonical 权限码常量，并由 controller、service、测试与种子数据共享同一词汇表。

#### Scenario: Use canonical management permissions
- **WHEN** 新增 `A-5~A-12` 接口落地
- **THEN** `AuthorizationPermissionCodes` 至少包含 `user.manage`、`user.import`、`role.manage`
- **AND** 对应 `@PreAuthorize`、测试数据与接口契约使用相同权限码

## REMOVED Requirements
- 无
