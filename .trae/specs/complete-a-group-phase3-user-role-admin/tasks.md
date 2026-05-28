# Tasks
- [x] Task 1: 补齐 `A-5` 用户分页查询链路
  - [x] SubTask 1.1: 新增用户分页 query/view/repository 契约，支持 `keyword/status/orgUnitId`
  - [x] SubTask 1.2: 新增 `GET /api/admin/users` controller、response DTO 与鉴权注解
  - [x] SubTask 1.3: 补齐 WebMvc、application、repository 定向测试

- [x] Task 2: 补齐 `A-6/A-7` 用户创建与状态修改链路
  - [x] SubTask 2.1: 新增创建用户与修改状态 command/service/repository 契约
  - [x] SubTask 2.2: 处理 `userNo` 唯一校验、`primaryOrgUnitId` 有效性校验与主组织归属建立
  - [x] SubTask 2.3: 处理 `ACTIVE/DISABLED/LOCKED` 状态校验、no-op/冲突语义与定向测试

- [x] Task 3: 补齐 `A-8` 用户批量导入最小可用闭环
  - [x] SubTask 3.1: 冻结 `multipart/form-data` 接口契约与 `ImportResult` 返回模型
  - [x] SubTask 3.2: 实现文件非空、`importMode`、模板列与重复编号语义校验
  - [x] SubTask 3.3: 补齐导入 controller/service 测试，明确 `INSERT_ONLY` 与 `UPSERT` 行为

- [x] Task 4: 补齐 `A-9/A-10/A-11` 角色模板查询与写入链路
  - [x] SubTask 4.1: 新增角色分页 query/view/repository 契约，返回 `permissionCount`
  - [x] SubTask 4.2: 实现创建角色模板，校验 `roleCode` 唯一与 `roleScope/status` 合法
  - [x] SubTask 4.3: 实现修改角色模板，仅允许更新 `roleName/roleScope/status`
  - [x] SubTask 4.4: 补齐 WebMvc、application、repository 定向测试

- [x] Task 5: 补齐 `A-12` 角色权限整集合绑定链路
  - [x] SubTask 5.1: 新增角色权限替换 command/service/repository 契约
  - [x] SubTask 5.2: 复用 `A-20` 权限字典校验权限码存在性
  - [x] SubTask 5.3: 实现 `replaceAll=true` 语义并补齐冲突/不存在场景测试

- [x] Task 6: 对齐鉴权词汇表、接口总表与回归验证
  - [x] SubTask 6.1: 扩展 `AuthorizationPermissionCodes` 与对应安全注解
  - [x] SubTask 6.2: 更新 `docs/reference/api-surface.md` 中新增接口面
  - [x] SubTask 6.3: 运行定向测试、`compile` 与诊断检查，确认本阶段闭环

# Task Dependencies
- `Task 2` depends on `Task 1`
- `Task 3` depends on `Task 2`
- `Task 5` depends on `Task 4`
- `Task 6` depends on `Task 1`, `Task 2`, `Task 3`, `Task 4`, `Task 5`
