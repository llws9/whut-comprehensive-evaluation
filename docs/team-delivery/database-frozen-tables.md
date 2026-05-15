# 数据库冻结表清单

## 1. 说明

本文档从 [database-schema-confirmation.md](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/docs/team-delivery/database-schema-confirmation.md) 中单独抽取“一期冻结的 19 张关系型核心表”，用于直接发给开发组做建模、建表和接口联调参考。

统一口径：

- 本期冻结表总数：`19`
- `application_submission` 物理主键统一为 `id`
- 平台运行时配置表、导入导出批次表、`student_profile/staff_profile` 不在本期冻结清单内

## 2. 冻结总表

| 序号 | 领域 | 表名 | 主键 | 作用 | 状态 |
|---:|---|---|---|---|---|
| 1 | 身份与权限域 | `iam_user` | `id` | 用户主表 | 冻结 |
| 2 | 身份与权限域 | `org_unit` | `id` | 组织树 | 冻结 |
| 3 | 身份与权限域 | `org_membership` | `id` | 用户组织归属 | 冻结 |
| 4 | 身份与权限域 | `iam_role` | `id` | 角色模板 | 冻结 |
| 5 | 身份与权限域 | `iam_permission` | `id` | 权限字典 | 冻结 |
| 6 | 身份与权限域 | `iam_role_permission` | `id` | 角色-权限关系 | 冻结 |
| 7 | 身份与权限域 | `iam_user_role_assignment` | `id` | 用户角色分配 | 冻结 |
| 8 | 身份与权限域 | `iam_scope_rule` | `id` | 数据范围规则 | 冻结 |
| 9 | 身份与权限域 | `iam_session` | `id` | 登录会话 | 冻结 |
| 10 | 综测定义域 | `evaluation_category` | `id` | 大类定义 | 冻结 |
| 11 | 综测定义域 | `evaluation_item` | `id` | 子项定义 | 冻结 |
| 12 | 申请域 | `application_submission` | `id` | 申请表头 | 冻结 |
| 13 | 申请域 | `application_fact` | `id` | 通用事实明细 | 冻结 |
| 14 | 申请域 | `application_review_log` | `id` | 审核轨迹 | 冻结 |
| 15 | 申请域 | `application_attachment` | `id` | 申请附件绑定 | 冻结 |
| 16 | 文件与附件域 | `file_asset` | `id` | 文件主表 | 冻结 |
| 17 | 文件与附件域 | `public_attachment_entry` | `id` | 公共附件池发布表 | 冻结 |
| 18 | 最终成绩域 | `final_record` | `id` | 学生学年最终成绩表头 | 冻结 |
| 19 | 最终成绩域 | `final_component_score` | `id` | 冻结后的分项明细 | 冻结 |

## 3. 分领域汇总

| 领域 | 表数量 | 表名 |
|---|---:|---|
| 身份与权限域 | 9 | `iam_user`、`org_unit`、`org_membership`、`iam_role`、`iam_permission`、`iam_role_permission`、`iam_user_role_assignment`、`iam_scope_rule`、`iam_session` |
| 综测定义域 | 2 | `evaluation_category`、`evaluation_item` |
| 申请域 | 4 | `application_submission`、`application_fact`、`application_review_log`、`application_attachment` |
| 文件与附件域 | 2 | `file_asset`、`public_attachment_entry` |
| 最终成绩域 | 2 | `final_record`、`final_component_score` |

## 4. 不在本期冻结范围内

以下对象已明确不纳入本期冻结清单：

- 平台运行时配置表：`platform_rule_config`、`menu_switch`、`submit_deadline`
- 导入导出批次表：`import_job`、`import_error_item`、`export_job`
- 资料拆表：`student_profile`、`staff_profile`
