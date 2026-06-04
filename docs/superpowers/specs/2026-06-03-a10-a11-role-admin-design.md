# A-10 / A-11 角色模板写接口设计说明

## 1. 目标与范围

本设计仅覆盖后端接口与测试（不含前端改造）：

- A-10 `POST /api/admin/roles`：创建角色模板，支持 `roleScope` 校验与持久化
- A-11 `PATCH /api/admin/roles/{roleId}`：修改角色模板，支持“字段快照并发校验”，冲突返回 `409 BIZ-4090`

遵循最小改动原则：在现有 `RoleAdminController`（当前仅查询）上增量扩展写接口，不做无关重构。

---

## 2. 现状与差距

### 2.1 已有能力

- 角色查询入口：`RoleAdminController#pageRoles`
- 角色查询应用服务：`DefaultRoleAdminQueryApplicationService`
- 角色表实体：`IamRoleDO`（字段含 `role_code/role_name/role_scope/status/created_at`）
- 权限常量：`AuthorizationPermissionCodes.ROLE_MANAGE`

### 2.2 当前缺口

- 缺少 A-10 创建角色接口与请求/响应模型
- 缺少 A-11 修改角色接口与快照并发校验
- 缺少角色写应用服务与对应仓储抽象
- 缺少 A-10/A-11 对应 WebMvc + 应用层 + 仓储测试

---

## 3. 设计决策

### 3.1 分层与职责

- **Interfaces 层**：参数校验、HTTP 映射、DTO 转换、鉴权注解
- **Application 层**：业务规则（唯一性、快照并发、状态校验）
- **Domain/Infra 层**：角色数据读写（按 roleId / roleCode 查询与保存）

沿用现有查询服务，不把写逻辑塞入 Query Service，避免职责混杂。

### 3.2 roleScope 校验策略

按本轮确认：**沿用现有值口径**（最小风险）。

- 允许值先收敛为当前主用值：`ORG_SUBTREE`
- 校验位置：Application Service（统一业务规则）
- 未来扩展可在常量集合中增量放开

### 3.3 A-11 快照并发语义

请求体包含两组字段：

- 目标值：`roleName/roleScope/status`
- 快照值：`snapshotRoleName/snapshotRoleScope/snapshotStatus`

处理规则：

1. 先按 `roleId` 查当前角色，不存在 -> `404 RES-4040`
2. 比较三项快照与当前值，任一不一致 -> `409 BIZ-4090`
3. 一致才执行更新

该语义不引入版本号字段，完全对齐 A 组文档约定。

---

## 4. 接口契约

### 4.1 A-10 创建角色模板

- 路由：`POST /api/admin/roles`
- 鉴权：`role.manage`
- 请求：
  - `roleCode`（必填）
  - `roleName`（必填）
  - `roleScope`（必填，当前仅 `ORG_SUBTREE`）
- 成功：`200 OK`，`data` 返回 `roleId/roleCode/roleName/roleScope/status`
- 失败：
  - 参数非法 -> `400 VAL-4001`
  - roleCode 重复 -> `409 BIZ-4090`
  - 无权限 -> `403 AUTH-4030`

### 4.2 A-11 修改角色模板

- 路由：`PATCH /api/admin/roles/{roleId}`
- 鉴权：`role.manage`
- 请求：
  - 目标：`roleName/roleScope/status`
  - 快照：`snapshotRoleName/snapshotRoleScope/snapshotStatus`
- 成功：`200 OK`，`data = null`
- 失败：
  - 参数非法 -> `400 VAL-4001`
  - 角色不存在 -> `404 RES-4040`
  - 快照冲突 -> `409 BIZ-4090`
  - 无权限 -> `403 AUTH-4030`

---

## 5. 测试设计（TDD）

1. **注解契约测试**（反射）
   - 确保 A-10/A-11 方法存在 `@PreAuthorize(ROLE_MANAGE)`
2. **WebMvc 测试**
   - A-10：200 / 400 / 403 / 409
   - A-11：200 / 400 / 403 / 404 / 409
3. **应用层单测**
   - roleScope 非法
   - roleCode 重复
   - 快照冲突
   - 正常更新
4. **仓储层单测（Mockito）**
   - 保存创建字段
   - 更新后字段落库

---

## 6. 非目标（本轮不做）

- A-12 `replaceAll` 权限绑定语义
- 角色相关前端页面/SDK 调整
- roleScope 枚举扩展到文档全量
- 角色查询链路重构

---

## 7. 风险与回滚

### 风险

- `roleScope` 口径过严可能挡住未来新值
- 快照比较若字段 trim/大小写策略不一致，可能出现误冲突

### 缓解

- 统一在应用层做 normalize（trim）
- 冲突消息写清楚，便于前端触发“刷新重试”

### 回滚

- 代码回滚顺序：A-11 -> A-10
- 若线上出现误阻断，可先回滚 A-11 的快照校验逻辑
