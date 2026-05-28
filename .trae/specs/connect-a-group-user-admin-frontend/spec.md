# A组前端用户管理接入 Spec

## Why
A 组后端已经补齐 `A-5~A-8` 的用户管理接口，但管理端前端仍停留在本地 mock 数据与假登录态，导致“用户列表、创建、状态修改、导入”无法形成真实联调闭环。需要单独收口用户管理前端，使管理端先具备最小可用的真实后台操作能力，再继续接角色模板与组织能力。

## What Changes
- 新增管理端用户 API 模块，对接 `A-5~A-8`：分页查询、创建用户、修改用户状态、批量导入。
- 将 `StudentsView.vue` 从本地 mock 数据收敛为真实后端数据源。
- 页面筛选条件按真实后端契约收敛为 `keyword + status`，不再保留 `grade + role` 假筛选。
- 保留现有页面骨架与交互布局，只做最小行为替换，不建设全局 HTTP 客户端。
- 用户创建、状态切换、导入成功后统一重新拉取列表，避免本地状态与服务端漂移。

## Impact
- Affected specs: `A-5`、`A-6`、`A-7`、`A-8`
- Affected code: `apps/manage/src/views/students/StudentsView.vue`、`apps/manage/src/api/users.ts`、`apps/manage/src/stores/auth.ts`

## ADDED Requirements
### Requirement: Frontend User List Must Use Real Backend Data
管理端系统 SHALL 使用 `GET /api/admin/users` 作为用户管理页的唯一列表数据源。

#### Scenario: Load first page successfully
- **WHEN** 管理员进入用户管理页
- **THEN** 前端调用 `GET /api/admin/users?pageNo=1&pageSize=20`
- **THEN** 表格显示真实返回的用户列表、状态、角色编码、组织归属与创建时间

#### Scenario: Filter by keyword and status
- **WHEN** 管理员输入关键字并选择用户状态后点击查询
- **THEN** 前端使用 `keyword` 和 `status` 作为查询参数重新请求列表
- **THEN** 页面不再使用本地 `grade` 或 `role` 假筛选

### Requirement: Frontend Must Support Creating Users
管理端系统 SHALL 支持通过 `POST /api/admin/users` 创建单个用户，并在成功后刷新用户列表。

#### Scenario: Create user successfully
- **WHEN** 管理员填写最小必需字段并提交
- **THEN** 前端调用 `POST /api/admin/users`
- **THEN** 创建成功后关闭表单并重新拉取列表

#### Scenario: Create user fails
- **WHEN** 后端返回 `VAL-4001`、`BIZ-4090` 或 `RES-4040`
- **THEN** 页面展示明确错误信息
- **THEN** 当前列表内容保持不变

### Requirement: Frontend Must Support Updating User Status
管理端系统 SHALL 支持通过 `PATCH /api/admin/users/{userId}/status` 切换用户状态。

#### Scenario: Toggle user status successfully
- **WHEN** 管理员点击用户状态按钮并确认目标状态
- **THEN** 前端调用 `PATCH /api/admin/users/{userId}/status`
- **THEN** 成功后重新拉取列表并展示最新状态

#### Scenario: Update status fails
- **WHEN** 后端返回 `BIZ-4090` 或 `RES-4040`
- **THEN** 页面展示错误提示
- **THEN** 前端不做本地假更新

### Requirement: Frontend Must Support Importing Users
管理端系统 SHALL 支持通过 `POST /api/admin/users/import` 上传文件并展示导入结果摘要。

#### Scenario: Import users successfully
- **WHEN** 管理员选择文件并触发导入
- **THEN** 前端使用 `multipart/form-data` 调用导入接口
- **THEN** 页面展示 `totalCount/successCount/failedCount/failedRows`
- **THEN** 导入成功后重新拉取用户列表

#### Scenario: Import users fails
- **WHEN** 后端返回 `VAL-4001`、`BIZ-4090` 或 `EXT-5033`
- **THEN** 页面展示导入失败原因
- **THEN** 不清空管理员已选择的查询条件

## MODIFIED Requirements
### Requirement: Students View Filtering Contract
用户管理页过滤条件 MUST 与后端 `A-5` 契约保持一致，仅支持 `keyword` 与 `status`，并保留分页参数。

#### Scenario: Replace mock filters with backend filters
- **WHEN** 用户管理页完成真实联调
- **THEN** 页面不再显示仅存在于本地 mock 的 `grade`、`role` 过滤条件
- **THEN** 页面保留现有布局风格，但筛选语义与真实接口一致

## REMOVED Requirements
### Requirement: Local Mock Student Records
**Reason**: 本地 mock 数据无法代表真实 IAM 用户管理能力，会导致联调结果失真。
**Migration**: 将 `StudentsView.vue` 中的本地数组、假状态切换与假角色变更替换为真实后端请求与刷新逻辑。
