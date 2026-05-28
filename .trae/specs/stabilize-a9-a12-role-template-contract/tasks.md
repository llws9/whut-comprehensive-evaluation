# Tasks
- [ ] Task 1: 冻结 `A-9 ~ A-12` 的最终字段契约
  - [ ] SubTask 1.1: 将 `A-9` 返回字段收敛为 `roleId/roleCode/roleName/roleScope/status/permissionCount/createdAt`
  - [ ] SubTask 1.2: 将 `A-10` 请求字段收敛为 `roleCode/roleName/roleScope`
  - [ ] SubTask 1.3: 将 `A-11` 更新字段收敛到当前 schema 支持字段，并写清快照并发保护语义
  - [ ] SubTask 1.4: 将 `A-12` 收敛为“整集合替换 + 支持空集合”

- [ ] Task 2: 修正文档与 API 总表
  - [ ] SubTask 2.1: 更新 `docs/team-delivery/group-a-identity-user-admin.md` 中 `A-9 ~ A-12` 的字段表、示例与错误码语义
  - [ ] SubTask 2.2: 更新 `docs/team-delivery/api-surface.md` 中角色模板相关摘要
  - [ ] SubTask 2.3: 移除残留的 `roleType/description` 旧口径

- [ ] Task 3: 明确后端实现前置条件
  - [ ] SubTask 3.1: 补齐 `role.manage` 权限常量，避免文档有契约但代码无对应权限码
  - [ ] SubTask 3.2: 核对 `A-9 ~ A-12` Controller/Request/Response 与文档是否一致
  - [ ] SubTask 3.3: 为 `A-10` 唯一键冲突、`A-11` 快照冲突、`A-12` 空集替换补齐测试证据

- [ ] Task 4: 明确前端接入前置边界
  - [ ] SubTask 4.1: 规定角色模板页入口权限使用 `role.manage`
  - [ ] SubTask 4.2: 规定权限绑定按钮使用 `permission.manage` 动作级控制
  - [ ] SubTask 4.3: 规定权限选择器唯一数据源为 `A-20 GET /api/admin/permissions`
  - [ ] SubTask 4.4: 明确 `PermissionsView.vue` 在本任务完成前不得继续按旧 mock 契约接真实接口

- [ ] Task 5: 完成契约收口验证
  - [ ] SubTask 5.1: 复核交付文档中不再出现 `roleType/description`
  - [ ] SubTask 5.2: 复核 `A-9` 返回中包含 `roleScope`
  - [ ] SubTask 5.3: 复核 `A-11` 文档已明确 `409 BIZ-4090` 的快照冲突语义
  - [ ] SubTask 5.4: 复核 `A-12` 文档已明确支持空权限集合整替换
  - [ ] SubTask 5.5: 复核角色模板页的页面权限与动作权限已分离定义

# Task Dependencies
- `Task 2` depends on `Task 1`
- `Task 3` depends on `Task 1`
- `Task 4` depends on `Task 1` and `Task 2`
- `Task 5` depends on `Task 2`、`Task 3`、`Task 4`
