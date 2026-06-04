# A-10 设计说明：POST /api/admin/roles

## 1. 目标与范围
本次仅交付 A-10：新增管理端角色创建接口 `POST /api/admin/roles`，按最小改动完成可测试闭环。

不在本次范围：A-11、A-12、A-8 及任何跨模块重构。

## 2. 架构与改动边界
- 在 `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/RoleAdminController.java` 新增 `@PostMapping`。
- 权限注解沿用现有管理端权限常量模式（与现有 IAM 管理接口一致）。
- Controller 仅负责 request/response 映射，不承载业务规则。
- 应用服务侧新增“创建角色”用例；不改动现有角色查询链路，避免范围扩散。

## 3. 数据流
1. 接收 `CreateRoleRequest`。
2. 映射为 `CreateRoleCommand`。
3. 调用 `RoleAdminApplicationService.createRole(...)`。
4. 返回 `RoleCreatedView`。
5. Controller 映射为响应 DTO，包装 `ApiResponse.success(...)`。

## 4. 业务规则与错误语义
### 4.1 业务规则
- `roleCode` 唯一，重复时返回冲突语义。
- 必填字段（至少 `roleCode`、`roleName`）不能为空。
- 新角色初始状态沿用现有角色模型默认值，不新增可选状态行为。

### 4.2 错误语义
- 参数/格式校验失败：`ValidationException`（400）。
- 业务冲突（如重复 `roleCode`）：复用现有业务异常（`BizException` 或项目既有等价异常）并映射为业务错误响应（409 语义）。

约束：不新增异常体系，不改全局异常处理器。

## 5. 测试策略（TDD）
### 5.1 RED
先新增并单独运行 WebMvc 测试，确认失败原因为“功能未实现”：
- 用例1：创建成功主链路（200，返回 roleCode/roleName）。
- 用例2：重复 roleCode 冲突场景（业务错误响应）。

### 5.2 GREEN
仅补齐让上述测试通过的最小实现：
- RoleAdminController 新增 POST 路由。
- 应用服务最小创建逻辑与冲突判定。

### 5.3 REFACTOR
仅做不改变行为的小步整理（命名、重复映射收敛），每步后保持全绿。

## 6. 验收标准
- `POST /api/admin/roles` 路由存在。
- 鉴权注解正确。
- 主流程测试通过。
- 冲突场景测试通过。
- 相关回归测试保持全绿。

## 7. 实施顺序
1. WebMvc 失败测试（主链路、冲突场景）。
2. 最小实现 Controller + Application Service。
3. 测试全绿后小步重构。
4. 回归角色域相关测试。