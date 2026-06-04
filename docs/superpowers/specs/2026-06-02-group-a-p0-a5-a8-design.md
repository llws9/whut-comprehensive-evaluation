# A组 P0 设计说明：A-5 用户分页真实查库 & A-8 批量导入真实导入

- 日期：2026-06-02
- 范围：`docs/team-delivery/group-a-identity-user-admin-relay-checklist.md` 中 P0 的 A-5、A-8
- 交付节奏：串行两次提交（先 A-5，后 A-8）
- 已确认约束：
  1. A-8 在 `INSERT_ONLY` 下遇到重复 `userNo` 时整批失败（409）并回滚。
  2. A-5 参数严格使用 `keyword`，不保留 `userName` 兼容参数。
  3. A-8 Excel 列头沿用仓库现有模板/测试样例。

---

## 1. 目标与非目标

### 1.1 目标

1. A-5：`GET /api/admin/users` 改为真实分页查库，支持 `pageNo/pageSize/keyword/status/orgUnitId`。
2. A-8：`POST /api/admin/users/import` 完成真实 Excel 解析、导入和结果统计。
3. 两项均满足最小可联调标准，并补齐最小自动化验证。

### 1.2 非目标

1. 不在本轮处理 A-6/A-4/A-10~A-12。
2. 不扩展新的导入模板规范，沿用现有模板列定义。
3. 不做与本次交付无关的重构（例如 IAM 全域模型调整）。

---

## 2. 现状摘要

1. `UserAdminApplicationService#pageUsers` 仍返回固定空页占位实现。
2. 当前 `/api/admin/users` 控制器参数仍是 `userName`，与文档约定的 `keyword` 不一致。
3. 仓储层已有基础分页能力，但 keyword 口径和 org 过滤能力需要按契约收口。
4. 代码中尚未形成 `/api/admin/users/import` 真实入口及导入编排逻辑。

---

## 3. 方案总览（采用方案 A）

按“契约先行 + 串行闭环”推进：

- 提交 1（A-5）：先把分页主链路打通并验收。
- 提交 2（A-8）：再落地批量导入、冲突语义和统计。

每个提交都包含：代码改动、最小验证、清单回填。

---

## 4. A-5 详细设计（用户分页真实查库）

### 4.1 接口契约

- 路由：`GET /api/admin/users`
- 参数：
  - `pageNo`（默认 1）
  - `pageSize`（默认 20）
  - `keyword`（可选，匹配 `userNo/userName`）
  - `status`（可选）
  - `orgUnitId`（可选）
- 鉴权：`user.manage`

### 4.2 分层职责

1. **Interfaces（Controller）**
   - 将入参从 `userName` 切换为 `keyword`。
   - 构造 `UserAdminPageQuery(pageNo, pageSize, keyword, status, orgUnitId)`。

2. **Application（Service）**
   - `UserAdminApplicationService#pageUsers` 去除占位空页逻辑。
   - 调用 `IamUserQueryRepository#pageUsers` 获取真实分页。
   - 完成 domain -> view 映射（保持现有响应结构）。

3. **Domain（Query model / Repository contract）**
   - `UserPageQuery` 语义调整为 `keyword`，用于统一表达模糊检索。

4. **Infra（MyBatis Repository）**
   - 分页查询增加组合过滤：
     - `keyword` -> `(user_no LIKE ? OR user_name LIKE ?)`
     - `status` -> 精确过滤
     - `orgUnitId` -> 基于 `org_membership` 关系过滤
   - 维持稳定排序（如按 `id` 升序）保证分页可预测。

### 4.3 错误处理

- 参数非法：返回 400 语义（由现有校验链路处理）。
- org 不存在：按现有资源校验策略返回 404 语义（若该校验由服务层承担则补齐）。
- 鉴权不足：403（沿用 `@PreAuthorize`）。

### 4.4 最小可交付（DoD）

1. 不再返回固定空页。
2. `keyword` 能命中 `userNo` 和 `userName`。
3. `status` 和 `orgUnitId` 过滤生效。
4. 自动化测试覆盖核心过滤组合与分页行为。

---

## 5. A-8 详细设计（批量导入真实导入）

### 5.1 接口契约

- 路由：`POST /api/admin/users/import`
- Content-Type：`multipart/form-data`
- 参数：
  - `file`（必填）
  - `importMode`（可选，`UPSERT` / `INSERT_ONLY`）
- 鉴权：`user.import`

### 5.2 分层职责

1. **Interfaces（Controller）**
   - 新增导入接口入口，接收文件与模式。
   - 基础参数校验：空文件、非法 mode。
   - 调用应用服务并返回统计结果。

2. **Application（Service）**
   - 负责导入编排：
     1) 解析 Excel（沿用现有模板列头）
     2) 行级校验并收集 `failedRows`
     3) 执行导入策略（UPSERT / INSERT_ONLY）
     4) 汇总 `totalCount/successCount/failedCount/failedRows`
   - `INSERT_ONLY` 冲突策略：
     - 若检测到任意重复 `userNo`，抛冲突异常 -> 409；
     - 整批事务回滚，不产生落库副作用。

3. **Domain / Repository**
   - 提供批量存在性查询与批量写入/更新能力（可复用现有 create/update 能力封装）。

4. **Infra（Parser + Persistence）**
   - Excel 解析实现读取模板列并携带源行号。
   - 数据层执行批量查询、插入、更新，保证事务一致性。

### 5.3 返回与异常语义

- 成功：返回真实统计和失败行摘要。
- 失败：
  - 空文件/非法 mode/模板错误 -> 400
  - `INSERT_ONLY` 重复冲突 -> 409
  - 解析依赖异常 -> 按现有外部依赖异常语义处理

### 5.4 最小可交付（DoD）

1. 导入接口可解析现有模板并真实落库。
2. 返回统计字段为真实值。
3. `failedRows` 含行号与失败原因。
4. `INSERT_ONLY` 冲突时整批 409 且无落库。
5. 自动化测试覆盖成功、部分失败、冲突回滚、参数异常。

---

## 6. 测试策略

### 6.1 A-5

- 应用层测试：
  - 分页参数透传
  - keyword 双字段匹配
  - status/orgUnitId 过滤组合
- WebMvc 测试：
  - 参数绑定为 `keyword`
  - 返回结构正确

### 6.2 A-8

- 应用层测试：
  - UPSERT 正常导入统计
  - 行级错误计入 `failedRows`
  - INSERT_ONLY 冲突触发 409 且回滚
- WebMvc 测试：
  - multipart 参数校验（空文件、非法 mode）
  - 成功/失败响应码与结构

---

## 7. 执行顺序与交付物

### 提交 1：A-5

- 交付物：
  - 分页接口参数与查询链路改造代码
  - 对应测试
  - 清单第 1 项状态回填与完成说明

### 提交 2：A-8

- 交付物：
  - 导入接口与应用编排代码
  - 对应测试
  - 清单第 2 项状态回填与完成说明

---

## 8. 风险与缓解

1. **导入模板实际列头与预期不一致**
   - 缓解：先以现有测试样例为真值，补模板校验错误提示。
2. **org 过滤 SQL 复杂导致分页性能波动**
   - 缓解：优先实现正确性，使用已有索引字段与稳定排序，后续按压测再优化。
3. **INSERT_ONLY 回滚边界遗漏**
   - 缓解：冲突检测与写入统一置于事务边界，测试显式校验“无落库”。

---

## 9. 验收清单映射

- A-5：
  - [x] 不再固定空列表
  - [x] keyword 支持 userNo/userName 模糊
  - [x] status/orgUnitId 过滤生效
- A-8：
  - [x] total/success/failed 真实统计
  - [x] failedRows 含行号与原因
  - [x] INSERT_ONLY 重复冲突语义（409 + 回滚）

---

## 10. 后续衔接

本设计完成后，进入 `writing-plans` 生成实现计划，并按“串行两次提交”执行。