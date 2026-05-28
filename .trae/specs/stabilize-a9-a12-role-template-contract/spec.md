# A组角色模板契约收敛 Spec

## Why
当前管理端前端的 `PermissionsView.vue` 仍停留在本地 mock，无法直接进入 `A-9 ~ A-12` 的真实联调。根因不是前端页面缺实现，而是角色模板相关契约本身仍不稳定：交付文档仍残留旧口径 `roleType/description`，而冻结 schema `iam_role` 与现有模型实际只有 `roleCode/roleName/roleScope/status`；`A-11` 的并发保护已明确采用字段快照而不是版本号；`A-12` 又依赖 `A-20` 权限字典作为唯一数据源。若不先冻结这组契约，前端接入角色模板管理会建立在错误字段和不稳定权限边界上，后续必然返工。

## What Changes
- 将 `A-9 ~ A-12` 的对外交付契约统一收敛到当前冻结 schema：使用 `roleScope/status`，彻底移除 `roleType/description`。
- 明确 `A-9` 的列表返回必须包含 `roleScope`，保证前端编辑链路能拿到当前模板的完整快照，不再额外猜测字段或依赖本地缓存。
- 明确 `A-10` 只允许创建当前 schema 支持的角色模板字段：`roleCode`、`roleName`、`roleScope`。
- 明确 `A-11` 只允许更新当前 schema 支持的字段，并将“字段快照冲突检测”升级为公开契约，而不是隐藏实现细节。
- 明确 `A-12` 继续采用“整集合替换”语义，且必须支持空集合替换；权限选择器唯一数据源为 `A-20`。
- 明确未来前端角色模板页的准入与动作权限边界：页面进入依赖 `role.manage`，权限绑定按钮依赖 `permission.manage`，避免再出现页面权限与接口权限脱节。

## Impact
- Affected specs: `A-9`、`A-10`、`A-11`、`A-12`、`A-20`
- Affected docs: `docs/team-delivery/group-a-identity-user-admin.md`、`docs/team-delivery/api-surface.md`
- Affected backend areas: 角色模板 Controller/Request/Response、权限常量、角色模板回归测试
- Affected frontend areas: `apps/manage/src/views/permissions/PermissionsView.vue`、`packages/shared/src/router/menu.ts`

## ADDED Requirements
### Requirement: Role Template Contract Must Follow Existing Schema
A 组角色模板相关接口 SHALL 以当前 `iam_role` 冻结 schema 作为唯一事实来源，对外只暴露 `roleCode`、`roleName`、`roleScope`、`status` 这组字段语义。

#### Scenario: Reject legacy roleType/description contract
- **WHEN** 团队整理 `A-9 ~ A-12` 的交付文档、接口示例或前端实现说明
- **THEN** 文档中不再出现 `roleType`
- **THEN** 文档中不再出现 `description`
- **THEN** 创建和编辑角色模板的契约统一使用 `roleScope`

### Requirement: A-9 List Response Must Support Edit Flow
`GET /api/admin/roles` SHALL 返回支撑前端编辑链路所需的角色模板快照字段，至少包含 `roleId/roleCode/roleName/roleScope/status/permissionCount/createdAt`。

#### Scenario: Load role template list
- **WHEN** 管理端页面查询角色模板列表
- **THEN** `A-9` 返回结果中包含 `roleScope`
- **THEN** 前端可以在不额外请求详情接口的前提下打开编辑态
- **THEN** 前端不再需要为当前角色模板补造本地 `roleType` 或假 `description`

### Requirement: A-10 Create Contract Must Be Schema-Aligned
`POST /api/admin/roles` SHALL 只接受当前 schema 支持的创建字段：`roleCode`、`roleName`、`roleScope`。

#### Scenario: Create role template successfully
- **WHEN** 管理员提交合法的 `roleCode`、`roleName`、`roleScope`
- **THEN** 后端创建角色模板并返回 `roleId/roleCode/roleName/roleScope/status`
- **THEN** 新建角色默认状态由后端按当前实现口径赋值

#### Scenario: Create role template with duplicated roleCode
- **WHEN** 管理员使用已存在的 `roleCode` 创建角色模板
- **THEN** 后端返回 `409 BIZ-4090`
- **THEN** 前端可以据此直接展示“角色编码重复”错误，而不是将其误判为服务异常

### Requirement: A-11 Update Contract Must Use Snapshot-Based Conflict Protection
`PATCH /api/admin/roles/{roleId}` SHALL 使用字段快照进行并发保护，而不是版本号机制；更新契约只允许覆盖当前 schema 支持的字段，并公开冲突语义。

#### Scenario: Update role template successfully
- **WHEN** 管理员基于最新角色模板快照提交更新
- **THEN** 请求体只包含当前 schema 支持的目标字段与并发校验所需的快照字段
- **THEN** 后端返回 `data = null`
- **THEN** 前端可在成功后重新拉取列表，而不必本地推演最终状态

#### Scenario: Reject stale snapshot update
- **WHEN** 管理员提交的角色模板快照已过期
- **THEN** 后端返回 `409 BIZ-4090`
- **THEN** 错误语义明确指向“角色模板已被他人更新”
- **THEN** 前端必须重新拉取当前角色模板快照，而不是覆盖服务端最新值

### Requirement: A-12 Permission Binding Must Be Full Replacement
`POST /api/admin/roles/{roleId}/permissions` SHALL 继续采用整集合替换语义，且允许将权限集合替换为空集。

#### Scenario: Replace all permissions with non-empty set
- **WHEN** 管理员为某个角色模板保存一组权限码
- **THEN** 后端以整集合替换方式更新角色权限关系
- **THEN** 前端不再依赖本地增量补丁语义

#### Scenario: Replace all permissions with empty set
- **WHEN** 管理员清空权限选择器并提交保存
- **THEN** 后端接受空的 `permissionCodes`
- **THEN** 角色模板原有权限关系被清空

### Requirement: Permission Dictionary Must Be The Only Selector Source
角色权限绑定页 SHALL 使用 `A-20 GET /api/admin/permissions` 作为唯一权限字典来源，不允许前端继续硬编码权限码或权限名称。

#### Scenario: Load permission dictionary before editing permissions
- **WHEN** 管理员进入角色模板权限绑定区域
- **THEN** 前端先查询 `A-20`
- **THEN** 权限列表按真实 `permissionCode/permissionName/permissionGroup/status` 展示
- **THEN** 页面上的权限项不再来自本地常量数组

### Requirement: Role Template Page Permission Contract Must Be Split By Action
未来管理端角色模板页 MUST 将“页面准入权限”和“权限绑定动作权限”拆开定义，避免单一权限码错误覆盖整页行为。

#### Scenario: Enter role template page with role.manage only
- **WHEN** 当前用户具备 `role.manage` 但不具备 `permission.manage`
- **THEN** 用户可以进入角色模板页并执行列表、创建、状态修改等 `A-9 ~ A-11` 行为
- **THEN** 权限绑定区域只读或禁用，不允许提交 `A-12`

#### Scenario: Block entering role template page without role.manage
- **WHEN** 当前用户不具备 `role.manage`
- **THEN** 前端路由不允许进入角色模板管理页
- **THEN** 页面权限码不得继续使用与后端无关的 `manage.permissions.view`

## MODIFIED Requirements
### Requirement: A-9 ~ A-12 Delivery Document Must Match Implemented Contract
`group-a-identity-user-admin.md` 中的 `A-9 ~ A-12` 必须修改为与当前实现和冻结 schema 一致的最终契约。

#### Scenario: Replace outdated role template fields in docs
- **WHEN** 文档完成本次收口
- **THEN** `A-9` 返回字段补入 `roleScope`
- **THEN** `A-10` 请求体由 `roleType/description` 改为 `roleScope`
- **THEN** `A-11` 的可修改字段改为当前 schema 支持字段，并补充快照冲突语义
- **THEN** `A-12` 明确支持空权限集合整替换

## REMOVED Requirements
### Requirement: Legacy Role Type And Description Contract
**Reason**: `roleType/description` 不存在于当前冻结 schema，也不是当前实现的真实对外交付能力。
**Migration**: 所有角色模板创建、编辑、列表展示与示例数据统一迁移到 `roleScope/status` 语义；前端实现必须以收口后的文档和真实接口为准，再开始接入 `PermissionsView.vue`。
