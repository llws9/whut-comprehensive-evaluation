# 数据库表责任矩阵

## 1. 文档目的

本文档用于把一期冻结的 `19` 张关系型核心表，按 A-E 五个开发组拆分成统一的数据责任矩阵，明确：

- 哪个组是该表的主责组
- 哪些组可以写
- 哪些组只能读
- 哪些组不允许直连该表

这份文档用于指导：

- 建模分工
- Repository/Mapper 归属
- 接口联调时的数据边界
- 避免多组同时改同一张表的结构或写入语义

## 2. 矩阵标记说明

| 标记 | 含义 |
|---|---|
| `OWN` | 主责组。负责表结构、主写链路、状态机定义、索引与核心仓储实现 |
| `RW` | 允许写，但不是表主责组；只能在主责组冻结契约下写入特定字段或记录 |
| `R` | 只读。允许查询、关联、汇总、鉴权，不允许写入 |
| `-` | 不允许直连该表；如有需要必须通过主责组接口或统一应用服务访问 |

统一原则：

- 表结构变更只能由 `OWN` 组提出并维护。
- `RW` 组不能私自新增字段、状态或索引。
- `R` 组不能为方便实现而绕过边界直接补写。
- 若某组需要从 `-` 升级到 `R/RW`，必须先改这份矩阵文档。

## 3. 总体分工结论

| 组别 | 主责领域 |
|---|---|
| A 组 | 身份、组织、角色、权限、范围规则、会话 |
| B 组 | 学生申请写链路、申请事实、申请附件绑定 |
| C 组 | 审核动作、审核轨迹、审核状态流转 |
| D 组 | 最终成绩汇总、最终成绩冻结、导入导出结果消费 |
| E 组 | 综测项目定义、文件主表、公共附件池 |

## 4. 19 张冻结表责任矩阵

| 序号 | 表名 | 领域 | 主责组 | A 组 | B 组 | C 组 | D 组 | E 组 | 备注 |
|---:|---|---|---|---|---|---|---|---|---|
| 1 | `iam_user` | 身份与权限 | A | `OWN` | `R` | `R` | `R` | `R` | 全局身份主表，其他组不得自行扩展 profile 表 |
| 2 | `org_unit` | 身份与权限 | A | `OWN` | `R` | `R` | `R` | `R` | 组织树由 A 组统一维护 |
| 3 | `org_membership` | 身份与权限 | A | `OWN` | `R` | `R` | `R` | `R` | 用户组织归属由 A 组维护 |
| 4 | `iam_role` | 身份与权限 | A | `OWN` | `R` | `R` | `R` | `R` | 角色模板由 A 组维护 |
| 5 | `iam_permission` | 身份与权限 | A | `OWN` | `R` | `R` | `R` | `R` | 权限字典由 A 组维护 |
| 6 | `iam_role_permission` | 身份与权限 | A | `OWN` | `R` | `R` | `R` | `R` | 角色权限绑定由 A 组维护 |
| 7 | `iam_user_role_assignment` | 身份与权限 | A | `OWN` | `R` | `R` | `R` | `R` | 角色分配状态机由 A 组维护 |
| 8 | `iam_scope_rule` | 身份与权限 | A | `OWN` | `R` | `R` | `R` | `R` | 范围规则解释与持久化由 A 组维护 |
| 9 | `iam_session` | 身份与权限 | A | `OWN` | `-` | `-` | `-` | `-` | 仅 A 组认证链路可直连，其他组通过登录态使用 |
| 10 | `evaluation_category` | 综测定义 | E | `R` | `R` | `R` | `R` | `OWN` | 分类定义由 E 组维护 |
| 11 | `evaluation_item` | 综测定义 | E | `R` | `R` | `R` | `R` | `OWN` | 项目定义由 E 组维护，B/C/D 仅消费 |
| 12 | `application_submission` | 申请域 | B | `R` | `OWN` | `RW` | `R` | `R` | B 组维护主写链路；C 组仅可写审核相关状态流转 |
| 13 | `application_fact` | 申请域 | B | `-` | `OWN` | `R` | `R` | `-` | 事实明细由 B 组维护，C/D 可读不可写 |
| 14 | `application_review_log` | 申请域 | C | `R` | `R` | `OWN` | `R` | `-` | 审核轨迹仅 C 组写入 |
| 15 | `application_attachment` | 申请域 | B | `-` | `OWN` | `R` | `R` | `R` | 附件绑定快照由 B 组写入，C/D/E 只读 |
| 16 | `file_asset` | 文件与附件 | E | `-` | `R` | `R` | `R` | `OWN` | 文件上传登记与文件读取授权由 E 组维护 |
| 17 | `public_attachment_entry` | 文件与附件 | E | `-` | `R` | `R` | `-` | `OWN` | 公共附件池发布与下架由 E 组维护 |
| 18 | `final_record` | 最终成绩 | D | `-` | `R` | `-` | `OWN` | `-` | 最终成绩表头由 D 组维护与确认 |
| 19 | `final_component_score` | 最终成绩 | D | `-` | `R` | `-` | `OWN` | `-` | 冻结后的分项明细仅 D 组写入 |

## 5. 受限写入说明

以下表虽然不是“单组独占写”，但写边界必须严格受控：

### 5.1 `application_submission`

- 主责组：B 组
- 受限写组：C 组
- 规则：
  - B 组负责草稿创建、更新、删除、提交、撤回。
  - C 组只允许写审核动作导致的状态流转，例如 `SUBMITTED -> APPROVED/RETURNED/REJECTED`。
  - D/E 组不得直接写申请表头。

### 5.2 `application_review_log`

- 主责组：C 组
- 规则：
  - 任何审核动作日志只能由 C 组统一写入。
  - B 组即使发起“学生撤回”，也不能复用这张表记录业务日志；若要记学生动作，应另行在业务事件或申请状态内表达。

### 5.3 `application_attachment`

- 主责组：B 组
- 规则：
  - 只允许 B 组在申请创建/更新时写入快照。
  - C 组只能读取已绑定快照并调用 E 组文件读取接口。
  - E 组不允许直接修改申请附件绑定关系。

## 6. 按组视角的读写边界

### 6.1 A 组

- 主写：`iam_user`、`org_unit`、`org_membership`、`iam_role`、`iam_permission`、`iam_role_permission`、`iam_user_role_assignment`、`iam_scope_rule`、`iam_session`
- 只读：`evaluation_category`、`evaluation_item`、`application_submission`、`application_review_log`
- 不应直连：申请事实、申请附件、文件主表、最终成绩表

### 6.2 B 组

- 主写：`application_submission`、`application_fact`、`application_attachment`
- 只读：A 组 IAM 全量表、`evaluation_category`、`evaluation_item`、`application_review_log`、`file_asset`、`public_attachment_entry`、`final_record`、`final_component_score`
- 不应直连：`iam_session`

### 6.3 C 组

- 主写：`application_review_log`
- 受限写：`application_submission`
- 只读：A 组 IAM 全量表、`evaluation_category`、`evaluation_item`、`application_fact`、`application_attachment`、`file_asset`、`public_attachment_entry`
- 不应直连：`iam_session`、`final_record`、`final_component_score`

### 6.4 D 组

- 主写：`final_record`、`final_component_score`
- 只读：A 组 IAM 全量表、`evaluation_category`、`evaluation_item`、`application_submission`、`application_fact`、`application_review_log`、`application_attachment`、`file_asset`
- 不应直连：`iam_session`、`public_attachment_entry`

### 6.5 E 组

- 主写：`evaluation_category`、`evaluation_item`、`file_asset`、`public_attachment_entry`
- 只读：A 组 IAM 全量表、`application_submission`、`application_attachment`
- 不应直连：`iam_session`、`application_fact`、`application_review_log`、`final_record`、`final_component_score`

## 7. Repository 与代码归属建议

为避免多组在同一模块重复建仓储，建议按以下原则落代码：

- A 组主责表的 Repository/Mapper 统一归 A 组模块维护
- B 组主责表的 Command Repository 归 B 组维护；C/D 组只补查询仓储或复用既有查询接口
- C 组对 `application_submission` 的写入应通过审核应用服务收口，不要单独再起一套“申请写仓储”
- D 组只维护最终成绩相关 Repository，不反向改申请域仓储
- E 组维护文件和项目定义 Repository，其它组优先通过 `fileId`、`itemCode` 契约消费，不直接扩写表结构

## 8. 联调时的红线

- 不允许任意组绕过矩阵，直接在别组主责表上补状态、补字段、补索引。
- 不允许为赶进度新增“临时表”“镜像表”“profile 表”绕开既有冻结结论。
- 若联调发现确实需要新增写权限，必须先同步修改本矩阵和对应组文档，再落代码。
