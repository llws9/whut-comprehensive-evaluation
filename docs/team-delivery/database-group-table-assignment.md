# 数据库建表分组分发说明

## 1. 目的

基于 `docs/team-delivery` 下的分组文档、冻结稿和责任矩阵，将一期 `19` 张核心关系表按五个开发组拆分为可直接执行的建表任务，并为每组提供独立 SQL 脚本。

本次输出只覆盖：

- 关系型核心表 DDL
- 可联调的初始化测试数据
- 每组负责的表边界
- 推荐执行顺序

本次明确不建：

- `platform_rule_config`
- `menu_switch`
- `submit_deadline`
- `import_job`
- `import_error_item`
- `export_job`
- `student_profile`
- `staff_profile`

原因见 `database-schema-confirmation.md`。

## 2. 分组与表归属

| 组别 | 职责 | 负责表 | 对应脚本 |
|---|---|---|---|
| A 组 | 身份、组织、角色、权限、范围规则、会话 | `iam_user`、`org_unit`、`org_membership`、`iam_role`、`iam_permission`、`iam_role_permission`、`iam_user_role_assignment`、`iam_scope_rule`、`iam_session` | `group-a-identity-user-admin.sql` |
| B 组 | 学生申请写链路、申请事实、申请附件绑定 | `application_submission`、`application_fact`、`application_attachment` | `group-b-student-application.sql` |
| C 组 | 审核动作、审核轨迹、审核状态流转 | `application_review_log` | `group-c-review-workflow.sql` |
| D 组 | 最终成绩汇总、冻结、导入导出结果消费 | `final_record`、`final_component_score` | `group-d-score-finalization-import-export.sql` |
| E 组 | 综测项目定义、文件主表、公共附件池 | `evaluation_category`、`evaluation_item`、`file_asset`、`public_attachment_entry` | `group-e-platform-governance-attachment-ai.sql` |

## 3. 推荐执行顺序

按职责分工归属，脚本有 5 份；按依赖关系，推荐执行顺序如下：

1. `group-a-identity-user-admin.sql`
2. `group-e-platform-governance-attachment-ai.sql`
3. `group-b-student-application.sql`
4. `group-c-review-workflow.sql`
5. `group-d-score-finalization-import-export.sql`

原因：

- B 组申请数据依赖 A 组用户/组织和 E 组项目定义/文件资产
- C 组审核日志依赖 B 组申请主链路
- D 组最终成绩依赖 B/C/E 的冻结输入

## 4. 测试数据策略

- 除角色、权限相关表外，其余表尽量提供 `10` 条以上测试数据
- 角色、权限相关表包括：
  - `iam_role`
  - `iam_permission`
  - `iam_role_permission`
- 对于这些“角色、权限相关”表，样例数据以“覆盖典型角色和权限集合”为目标，不强制追求 `10` 条以上
- 其余表均提供多样性样例，覆盖：
  - 启用 / 禁用
  - 草稿 / 提交 / 退回 / 通过 / 拒绝 / 撤回
  - 自传附件 / 公共附件
  - 不同组织、不同学年、不同分项来源

## 5. 红线

- A 组拥有 IAM 表结构解释权，其他组只读或受限写
- B 组拥有 `application_submission`、`application_fact`、`application_attachment` 主写权
- C 组只能写 `application_review_log`，以及对 `application_submission` 做受限状态流转
- D 组只写最终成绩，不反向写申请和审核链路
- E 组只写项目定义、文件资产、公共附件池，不写申请事实和最终成绩

## 6. 产物清单

- [group-a-identity-user-admin.sql](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/docs/team-delivery/group-a-identity-user-admin.sql)
- [group-b-student-application.sql](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/docs/team-delivery/group-b-student-application.sql)
- [group-c-review-workflow.sql](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/docs/team-delivery/group-c-review-workflow.sql)
- [group-d-score-finalization-import-export.sql](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/docs/team-delivery/group-d-score-finalization-import-export.sql)
- [group-e-platform-governance-attachment-ai.sql](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/docs/team-delivery/group-e-platform-governance-attachment-ai.sql)
