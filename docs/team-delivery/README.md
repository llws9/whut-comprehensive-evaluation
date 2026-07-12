# 团队交付文档入口

本文档集面向 `rewrite/whut-comprehensive-evaluation` 的 5 组并行开发场景，目标是把“每组做什么、需要实现哪些接口、数据库边界是否已冻结、底座怎么接入”一次性讲清楚。

## 文档清单

| 文档 | 对应组别 / 用途 | 说明 |
|---|---|---|
| `group-a-identity-user-admin.md` | A 组：身份认证 + 用户权限 | 登录、用户、角色、组织、角色分配、范围规则 |
| `group-b-student-application.md` | B 组：学生申请 | 学生申请草稿、修改、删除、提交、撤回、申请查询 |
| `group-c-review-workflow.md` | C 组：审核工作流 | 审核列表、详情、附件查看、审批、批量审批、审核轨迹 |
| `group-d-score-finalization-import-export.md` | D 组：成绩汇总 + 导入导出 | 学生成绩查询、最终提交、教师导入导出、未提交名单 |
| `group-e-platform-governance-attachment-ai.md` | E 组：平台治理 + 附件/AI | 开关、截止时间、项目定义、文件上传、公共附件池、AI 报告 |
| `database-schema-confirmation.md` | 全局数据库确认 | 哪些表已冻结、哪些还模糊、推荐最终表结构示意 |
| `database-frozen-tables.md` | 全局数据库冻结清单 | 单独导出的 19 张核心表 Markdown 总表 |
| `database-frozen-tables.csv` | 全局数据库冻结清单 | 可直接用 Excel 打开的 19 张核心表 CSV |
| `database-table-ownership-matrix.md` | 全局数据库责任矩阵 | 按 A-E 组拆分表主责、读写权限、联调红线 |
| `iam-scope-rule-ui-storage-query-design.md` | IAM 范围规则设计补充 | 管理端权限分配页面字段、`iam_scope_rule` 存储映射、查询 SQL 拼接方式 |
| `delivery-master-checklist.md` | 全局总控清单 | 合并数据库责任矩阵、接口依赖矩阵、交付顺序与最小交付项 |
| `foundation-capabilities-guide.md` | 全局底座说明 | 认证、仓储调用、文件上传、配置、日志、异常、开发规范 |
| `full-seed-init-smoke-gate.md` | 全局初始化检查 | A + E/B/C/D 初始化顺序、H2 CI gate、真实 MySQL smoke gate |
| `minimum-business-loop-demo-runbook.md` | 全局演示验收 | 登录、学生申请、审核、最终成绩提交与确认的最小业务闭环 |


## 当前实现状态标识

- `CURRENT_IMPLEMENTED`：已与当前 Java Controller / Mapper / Service 对齐，可作为当前联调契约。
- `PARTIAL_IMPLEMENTED`：已有部分接口或表结构落地，但文档仍包含目标态内容，未实现部分不得直接作为联调契约。
- `DEFERRED_AFTER_MINIMAL_D`：目标态接口已冻结在文档中，但不属于当前 Minimal D 实现和 smoke gate 覆盖范围。
- `TARGET_BLUEPRINT`：交付设计目标，不代表当前代码已经实现。
- `SQL_NEEDS_SYNC`：脚本曾发现与运行代码不一致，使用前必须先跑一致性校验。

## 推荐阅读顺序

1. 项目负责人先读 `delivery-master-checklist.md`，把数据库责任、接口依赖和交付顺序整体过一遍。
2. 再读 `database-schema-confirmation.md`，确认冻结表名和主键口径。
3. 对开发组分发时，优先发送 `database-frozen-tables.md` 或 `database-frozen-tables.csv`。
4. 5 个组长分别阅读自己的模块文档，确认接口范围、依赖和排期。
5. 全员统一阅读 `foundation-capabilities-guide.md`，避免各组重复造底座。
6. 改动任一组 SQL、safe-init 或跨组种子数据前，阅读 `full-seed-init-smoke-gate.md`，确认本地命令和 CI job。
7. 准备演示、验收或主链路回归前，阅读 `minimum-business-loop-demo-runbook.md`，确认最小业务闭环和自动 smoke gate。
8. 组间联调前回看 `delivery-master-checklist.md` 和 `database-schema-confirmation.md`，避免口径漂移。

## 统一约定

- 这些文档描述的是“目标实现接口”，允许与旧系统接口不同；是否已落地以各文档的当前实现状态标识为准。
- 如果文档写的是“建议冻结”，默认表示项目层面应优先按该方案落地，除非负责人另行拍板。
- HTTP 成功响应统一采用 `ApiResponse<T>`：`success/code/message/data`。
- HTTP 异常响应统一由 `GlobalExceptionHandler` 映射，常见错误码包括：`VAL-4001`、`AUTH-4010`、`AUTH-4030`、`RES-4040`、`BIZ-4090`、`CFG-5031`、`EXT-5033`、`SYS-5000`。
