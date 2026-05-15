# 数据库表冻结稿

## 1. 文档目的

本文档用于解决两个问题：

1. 当前 rewrite 目标库里，哪些表名和表职责已经足够清晰，可以直接冻结。
2. 本期不落库或不拆表的对象，如何用明确结论替代历史未决口径。

本文档不等同于正式 DDL 脚本，但它应作为“建表前的最终冻结口径”。

## 2. 已完成冻结的关键决策

### 2.1 申请主表命名仍有分歧

现有文档中同时出现过两套名字：

- `application_form`
- `application_submission`

建议冻结为：`application_submission`

理由：

- rewrite 当前正式写入模型已经以 `ApplicationSubmission` 命名。
- “submission” 更符合“学生提交的一条正式申请”语义。
- 后续 controller、application service、repository 命名可保持一致。

### 2.2 是否需要单独 `application_fact`

当前存在两种思路：

- 方案 A：所有业务描述都塞在 `application_submission` 上，附件单独表。
- 方案 B：申请表头与申请事实明细拆开，即 `application_submission + application_fact`。

建议冻结为：保留 `application_fact`。

理由：

- 后续子项扩展时更稳定。
- 便于把分值、文本说明、证据数量、额外 JSON 从表头中分离。
- 未来导入型记录和申请型记录更容易共用统一事实结构。

### 2.3 项目定义表命名有分歧

现有文档中出现：

- `evaluation_item`
- `score_item_definition`

建议冻结为：

- `evaluation_category`
- `evaluation_item`

理由：

- `evaluation_item` 语义比 `score_item_definition` 更宽，既能描述申请项，也能描述导入项和审核配置。
- 与 A 组权限模型中的 `category_code + item_code` 一致。

### 2.4 平台规则真源与管理方式

最终冻结为：

- 运行时真源统一为 Nacos typed config。
- 管理端接口负责修改 typed config，并作为唯一对外控制入口。
- 本期不新增 `platform_rule_config`、`menu_switch`、`submit_deadline` 三张关系型表。

理由：

- 满足“通过管理端接口控制”的产品诉求，同时保持运行期单一真源。
- 避免数据库配置和 Nacos 配置双写双读带来的漂移问题。
- 平台开关和截止时间属于运行时规则，不属于关系型主数据。

### 2.5 导入导出批次表

最终冻结为：

- D 组一期统一采用“同步导入/同步导出 + 即时回执”模式。
- 本期不新增 `import_job`、`import_error_item`、`export_job` 三张表。

理由：

- 当前接口契约已经冻结为同步处理和 `failedRows` 即时返回。
- 一期不引入异步任务表，可以显著降低实现复杂度与联调成本。
- 若后续升级为异步任务，再以二期增量建表处理，不影响当前表设计。

### 2.6 AI 报告存储不纳入本次关系型库冻结

当前 AI 报告更适合保持在 Mongo，暂不纳入本次 MySQL 表确认。

### 2.6 AI 报告存储

最终冻结为：

- AI 报告当前继续落 Mongo，不纳入本次 MySQL 表冻结范围。

### 2.7 用户资料拆表

最终冻结为：

- 本期不拆 `student_profile`、`staff_profile`。
- 用户资料统一以 `iam_user + org_membership` 为主。

理由：

- 当前 5 个组的接口都已基于统一身份主表建模。
- 一期优先收敛身份模型，避免学生/教师资料双轨结构增加维护成本。

### 2.8 申请主表主键命名

最终冻结为：

- `application_submission` 物理主键字段统一使用 `id`。
- 业务接口与文档层仍继续使用 `applicationId` 作为对外语义名称。

理由：

- 与仓库内其他核心表保持统一主键风格。
- 兼容代码层现有 `applicationId` 语义，不增加接口变更成本。

## 3. 冻结后的关系型库总表清单

## 3.1 身份与权限域

| 表名 | 主键 | 作用 | 状态 |
|---|---|---|---|
| `iam_user` | `id` | 用户主表 | 建议冻结 |
| `org_unit` | `id` | 组织树 | 建议冻结 |
| `org_membership` | `id` | 用户组织归属 | 建议冻结 |
| `iam_role` | `id` | 角色模板 | 建议冻结 |
| `iam_permission` | `id` | 权限字典 | 建议冻结 |
| `iam_role_permission` | `id` | 角色-权限关系 | 建议冻结 |
| `iam_user_role_assignment` | `id` | 用户角色分配 | 建议冻结 |
| `iam_scope_rule` | `id` | 数据范围规则 | 建议冻结 |
| `iam_session` | `id` | 登录会话 | 建议冻结 |

## 3.2 综测定义域

| 表名 | 主键 | 作用 | 状态 |
|---|---|---|---|
| `evaluation_category` | `id` | 大类定义 | 建议冻结 |
| `evaluation_item` | `id` | 子项定义 | 建议冻结 |

## 3.3 申请域

| 表名 | 主键 | 作用 | 状态 |
|---|---|---|---|
| `application_submission` | `id` | 申请表头 | 建议冻结 |
| `application_fact` | `id` | 通用事实明细 | 建议冻结 |
| `application_review_log` | `id` | 审核轨迹 | 建议冻结 |
| `application_attachment` | `id` | 申请附件绑定 | 建议冻结 |

## 3.4 文件与附件域

| 表名 | 主键 | 作用 | 状态 |
|---|---|---|---|
| `file_asset` | `id` | 文件主表 | 已较清晰 |
| `public_attachment_entry` | `id` | 公共附件池发布表 | 已较清晰 |

## 3.5 最终成绩域

| 表名 | 主键 | 作用 | 状态 |
|---|---|---|---|
| `final_record` | `id` | 学生学年最终成绩表头 | 建议冻结 |
| `final_component_score` | `id` | 冻结后的分项明细 | 建议冻结 |

## 4. 建议冻结后的核心表结构示意

以下是建议冻结的最小字段示意，不是正式 DDL，但足够指导各组建模。

### 4.1 `iam_user`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigint` | 主键 |
| `user_no` | `varchar(64)` | 学号/工号，唯一 |
| `user_name` | `varchar(128)` | 姓名 |
| `email` | `varchar(128)` | 邮箱 |
| `phone` | `varchar(32)` | 手机号 |
| `password_hash` | `varchar(255)` | 密码摘要 |
| `status` | `varchar(32)` | `ACTIVE/DISABLED/LOCKED` |
| `created_at` | `datetime` | 创建时间 |
| `updated_at` | `datetime` | 更新时间 |

### 4.2 `org_unit`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigint` | 主键 |
| `parent_id` | `bigint` | 父节点 |
| `unit_type` | `varchar(32)` | `SCHOOL/COLLEGE/GRADE/CLASS/ASSOCIATION/DEPARTMENT` |
| `unit_code` | `varchar(64)` | 编码，唯一 |
| `unit_name` | `varchar(128)` | 名称 |
| `path` | `varchar(1024)` | 树路径 |
| `status` | `varchar(32)` | 状态 |

### 4.3 `iam_user_role_assignment`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigint` | 主键 |
| `user_id` | `bigint` | 用户 ID |
| `role_id` | `bigint` | 角色 ID |
| `org_unit_id` | `bigint` | 角色挂载组织 |
| `source_type` | `varchar(32)` | `MANUAL/SYSTEM/IMPORT` |
| `effective_from` | `datetime` | 生效时间 |
| `effective_to` | `datetime` | 失效时间 |
| `status` | `varchar(32)` | `ACTIVE/INACTIVE/EXPIRED` |
| `assigned_by` | `bigint` | 分配人 |

### 4.4 `iam_scope_rule`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigint` | 主键 |
| `assignment_id` | `bigint` | 角色分配 ID |
| `permission_code` | `varchar(128)` | 权限码 |
| `scope_type` | `varchar(32)` | `SELF/ALL/ORG_UNIT/ORG_SUBTREE/CATEGORY/ITEM/ORG_UNIT_ITEM/CUSTOM_EXPRESSION` |
| `org_unit_id` | `bigint` | 组织范围 |
| `category_code` | `varchar(64)` | 大类编码 |
| `item_code` | `varchar(64)` | 子项编码 |
| `expression_json` | `json` | 复杂规则 |
| `priority` | `int` | 优先级 |
| `status` | `varchar(32)` | 状态 |

### 4.5 `evaluation_item`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigint` | 主键 |
| `category_code` | `varchar(64)` | 大类编码 |
| `item_code` | `varchar(64)` | 子项编码，唯一 |
| `item_name` | `varchar(128)` | 名称 |
| `apply_mode` | `varchar(32)` | `STUDENT_APPLY/TEACHER_IMPORT/MIXED` |
| `review_mode` | `varchar(32)` | 审核模式 |
| `score_mode` | `varchar(32)` | 计分模式 |
| `cap_rule_json` | `json` | 封顶规则 |
| `status` | `varchar(32)` | 状态 |

### 4.6 `application_submission`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigint` | 主键 |
| `applicant_user_id` | `bigint` | 申请人 |
| `org_unit_id` | `bigint` | 归属组织 |
| `category_code` | `varchar(64)` | 大类编码 |
| `item_code` | `varchar(64)` | 子项编码 |
| `academic_year` | `varchar(32)` | 学年 |
| `term` | `varchar(16)` | 学期 |
| `title` | `varchar(255)` | 标题 |
| `description` | `text` | 说明 |
| `status` | `varchar(32)` | `DRAFT/SUBMITTED/RETURNED/APPROVED/REJECTED/WITHDRAWN` |
| `submitted_at` | `datetime` | 提交时间 |
| `created_at` | `datetime` | 创建时间 |
| `updated_at` | `datetime` | 更新时间 |
| `version` | `bigint` | 乐观锁版本 |

### 4.7 `application_fact`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigint` | 主键 |
| `application_id` | `bigint` | 所属申请，对应 `application_submission.id` |
| `score_value` | `decimal(10,2)` | 分值 |
| `display_text` | `varchar(1000)` | 说明文本 |
| `evidence_count` | `int` | 证据数量 |
| `extra_json` | `json` | 额外业务字段 |
| `created_at` | `datetime` | 创建时间 |
| `updated_at` | `datetime` | 更新时间 |

### 4.8 `application_review_log`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigint` | 主键 |
| `application_id` | `bigint` | 申请 ID，对应 `application_submission.id` |
| `action` | `varchar(32)` | `APPROVE/RETURN/REJECT/WITHDRAW` |
| `reviewer_id` | `bigint` | 操作人 |
| `review_role` | `varchar(64)` | 操作角色 |
| `reason` | `varchar(1000)` | 审核意见 |
| `reviewed_at` | `datetime` | 审核时间 |

### 4.9 `application_attachment`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigint` | 主键 |
| `application_id` | `bigint` | 申请 ID，对应 `application_submission.id` |
| `file_id` | `varchar(64)` | 文件业务 ID |
| `selected_source` | `varchar(32)` | `SELF_UPLOAD/PUBLIC_POOL` |
| `sort_no` | `int` | 顺序 |
| `snapshot_filename` | `varchar(255)` | 文件名快照 |
| `snapshot_content_type` | `varchar(128)` | 类型快照 |
| `snapshot_size` | `bigint` | 大小快照 |
| `snapshot_storage_key` | `varchar(512)` | key 快照 |
| `created_at` | `datetime` | 创建时间 |

### 4.10 `file_asset`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigint` | 主键 |
| `file_id` | `varchar(64)` | 稳定业务文件 ID |
| `storage_key` | `varchar(512)` | 对象存储 key |
| `bucket` | `varchar(128)` | bucket |
| `original_filename` | `varchar(255)` | 原始文件名 |
| `content_type` | `varchar(128)` | MIME |
| `size` | `bigint` | 字节大小 |
| `uploader_user_id` | `bigint` | 上传人 |
| `uploader_type` | `varchar(32)` | `USER/ADMIN/SYSTEM` |
| `upload_channel` | `varchar(32)` | `SELF_UPLOAD/ADMIN_UPLOAD/SYSTEM_IMPORT` |
| `status` | `varchar(32)` | `ACTIVE/DELETED/ARCHIVED` |

### 4.11 `public_attachment_entry`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigint` | 主键 |
| `file_id` | `varchar(64)` | 关联 `file_asset.file_id` |
| `display_name` | `varchar(255)` | 展示名 |
| `description` | `varchar(1000)` | 说明 |
| `category_code` | `varchar(64)` | 分类 |
| `scope_type` | `varchar(32)` | `ALL/ORG_UNIT/ROLE` |
| `scope_value` | `varchar(128)` | 范围值 |
| `status` | `varchar(32)` | `DRAFT/PUBLISHED/OFFLINE` |
| `published_by` | `bigint` | 发布人 |
| `published_at` | `datetime` | 发布时间 |
| `sort_no` | `int` | 排序 |

### 4.12 `final_record`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigint` | 主键 |
| `student_user_id` | `bigint` | 学生 ID |
| `academic_year` | `varchar(32)` | 学年 |
| `status` | `varchar(32)` | `DRAFT/SUBMITTED/CONFIRMED` |
| `moral_total` | `decimal(10,2)` | 德育总分 |
| `intellectual_total` | `decimal(10,2)` | 智育总分 |
| `physical_total` | `decimal(10,2)` | 体育总分 |
| `labor_total` | `decimal(10,2)` | 劳育总分 |
| `grand_total` | `decimal(10,2)` | 总分 |
| `submitted_at` | `datetime` | 提交时间 |
| `confirmed_at` | `datetime` | 确认时间 |
| `version` | `bigint` | 乐观锁版本 |

### 4.13 `final_component_score`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `bigint` | 主键 |
| `final_record_id` | `bigint` | 所属最终记录 |
| `category_code` | `varchar(64)` | 大类编码 |
| `item_code` | `varchar(64)` | 子项编码 |
| `score_value` | `decimal(10,2)` | 分值 |
| `display_text` | `varchar(1000)` | 文字说明 |
| `source_type` | `varchar(32)` | 来源 |
| `source_ref_id` | `varchar(64)` | 来源记录 ID |
| `created_at` | `datetime` | 创建时间 |

## 5. 本期明确不建表或不拆表的对象

| 对象 | 最终结论 | 说明 |
|---|---|---|
| `platform_rule_config` | 本期不建表 | 平台规则统一走管理端接口 + Nacos typed config |
| `menu_switch` | 本期不建表 | 运行时真源不放数据库 |
| `submit_deadline` | 本期不建表 | 运行时真源不放数据库 |
| `import_job` | 本期不建表 | D 组一期同步导入 |
| `import_error_item` | 本期不建表 | 失败行通过 `failedRows` 即时回执 |
| `export_job` | 本期不建表 | D 组一期同步导出 |
| `student_profile` | 本期不拆表 | 统一收敛到 `iam_user + org_membership` |
| `staff_profile` | 本期不拆表 | 统一收敛到 `iam_user + org_membership` |

## 6. 结论

如果项目现在就要开始正式建表，建议冻结以下 19 张关系型核心表：

- `iam_user`
- `org_unit`
- `org_membership`
- `iam_role`
- `iam_permission`
- `iam_role_permission`
- `iam_user_role_assignment`
- `iam_scope_rule`
- `iam_session`
- `evaluation_category`
- `evaluation_item`
- `application_submission`
- `application_fact`
- `application_review_log`
- `application_attachment`
- `file_asset`
- `public_attachment_entry`
- `final_record`
- `final_component_score`

以上冻结清单已经消除当前文档中的历史未决项。一期内各组不得再并行发明替代表名、临时 profile 表或运行时配置表。
