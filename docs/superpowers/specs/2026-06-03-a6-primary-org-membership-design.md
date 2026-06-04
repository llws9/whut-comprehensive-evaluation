# A-6 设计说明：创建用户时落库主组织归属

- 日期：2026-06-03
- 目标项：A-6 创建用户补齐 `primaryOrgUnitId` 落库
- 关联清单：`docs/team-delivery/group-a-identity-user-admin-relay-checklist.md`

---

## 1. 背景与目标

当前创建用户接口 `POST /api/admin/users` 已接收 `primaryOrgUnitId`，但业务未使用，导致创建后用户缺少主组织归属。

本次目标：

1. 当请求携带 `primaryOrgUnitId` 时，创建用户后同步写入 `org_membership` 主组织记录。
2. 当 `primaryOrgUnitId` 不存在时，返回 404 语义（`ResourceNotFoundException`）。
3. 保持最小改动，不扩展到 A-22 查询逻辑重构。

---

## 2. 范围

### In Scope

- `UserAdminApplicationService#createUser` 增加主组织归属编排。
- 组织存在性校验（按 ID）。
- `org_membership` 主归属记录写入。
- 覆盖 A-6 必要单测与 WebMvc 用例。

### Out of Scope

- 不修改创建用户接口响应结构。
- 不引入组织状态额外校验（仅校验存在性）。
- 不调整 A-22 查询逻辑。

---

## 3. 方案对比与选型

### 方案 A（采用）：在创建用户应用服务内编排

- 在 `UserAdminApplicationService#createUser` 中按顺序完成：
  1) 查重；2) 组织存在校验；3) 创建用户；4) 写入主归属。
- 使用现有事务边界，确保原子性。

**优点**：改动小、契约闭环快、符合当前应用服务编排风格。  
**缺点**：创建用户服务引入少量组织归属写入逻辑。

### 方案 B：复用 `UserMembershipAdminApplicationService#replaceMemberships`

- 创建用户后调用 replace 接口写单条归属。

**优点**：复用现有 memberships 管理路径。  
**缺点**：路径偏重，存在不必要的读锁与替换语义成本。

### 方案 C：在用户仓储 createUser 一次性跨表写入

**优点**：调用方简洁。  
**缺点**：仓储职责过重，跨聚合语义下沉 infra，不利于维护。

---

## 4. 目标架构与职责

### 4.1 接口层（不变）

- `UserAdminController#createUser` 继续透传 `primaryOrgUnitId` 到 `CreateUserCommand`。

### 4.2 应用层（新增编排）

- `UserAdminApplicationService#createUser`：
  - 若 `primaryOrgUnitId != null`，先校验组织存在。
  - 创建 `iam_user`。
  - 若 `primaryOrgUnitId != null`，写入主组织归属记录。

### 4.3 领域仓储接口

- 复用组织查询：`OrgUnitLookupRepository#findById`。
- 扩展组织归属写接口：在 `UserMembershipAdminRepository` 增加主归属写入方法（单条插入）。

### 4.4 基础设施层

- `MybatisPlusUserMembershipAdminRepository` 实现新增方法，插入 `org_membership`。

---

## 5. 数据流（CreateUser）

1. 控制器接收请求并构建 `CreateUserCommand`。
2. 应用服务检查 `userNo` 是否重复（现有逻辑）。
3. 若 `primaryOrgUnitId` 非空，调用组织查询仓储校验存在性：
   - 不存在 -> 抛 `ResourceNotFoundException("组织不存在: {id}")`。
4. 应用服务创建用户（现有逻辑）。
5. 若 `primaryOrgUnitId` 非空，插入主组织归属。
6. 返回 `UserCreatedView`。

---

## 6. 归属写入字段约定

插入 `org_membership` 时字段如下：

- `user_id` = 新创建用户 ID
- `org_unit_id` = `primaryOrgUnitId`
- `membership_type` = `MANUAL`
- `is_primary` = `true`
- `status` = `ACTIVE`
- `joined_at` = 当前时间
- `left_at` = `null`

说明：该约定与现有 membership 管理链路的默认新增语义一致。

---

## 7. 事务与错误处理

- `createUser` 保持 `@Transactional`。
- 用户创建与主组织归属写入在同一事务中：任一步失败整体回滚。

错误语义：

- 用户编号重复：`ConflictException`（409，现有行为）。
- 组织不存在：`ResourceNotFoundException`（404）。
- 其他数据访问异常：沿用全局异常处理。

---

## 8. 测试设计（最小可行）

### 8.1 Application Service 单测

新增场景：

1. `primaryOrgUnitId` 有效 -> 创建用户成功且调用主归属写入仓储方法。
2. `primaryOrgUnitId` 无效 -> 抛 `ResourceNotFoundException`，且不调用用户创建。

### 8.2 WebMvc 测试

新增场景：

- 当应用服务抛出 `ResourceNotFoundException`（组织不存在）时，接口返回 404 对应错误码。

### 8.3 （可选）Repository 集成测试

- 验证新增写入方法落库字段正确（`is_primary=true` 等）。

---

## 9. 变更文件清单（预期）

- `whut-eval-application/.../service/UserAdminApplicationService.java`
- `whut-eval-domain/.../repository/UserMembershipAdminRepository.java`
- `whut-eval-infra/.../repository/MybatisPlusUserMembershipAdminRepository.java`
- `whut-eval-app/.../UserAdminApplicationServiceTest.java`
- `whut-eval-app/.../UserAdminControllerWebMvcTest.java`
- `docs/team-delivery/group-a-identity-user-admin-relay-checklist.md`（完成后更新状态）

---

## 10. 风险与规避

1. **跨表写入遗漏事务**：通过复用 `@Transactional` 保证原子性。
2. **错误语义不一致**：统一用 `ResourceNotFoundException`，由全局异常映射 404。
3. **接口行为漂移**：不变更 response 结构，只补齐归属写入行为。

---

## 11. 验收标准（A-6）

- 创建用户时传入有效 `primaryOrgUnitId`，用户创建成功且存在主组织归属记录。
- 传入不存在的 `primaryOrgUnitId`，返回 404 语义。
- 不传 `primaryOrgUnitId` 时，创建用户行为与现状兼容。
