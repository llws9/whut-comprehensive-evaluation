# whut-comprehensive-evaluation

新一代武汉理工大学综测平台后端重写工程。

## 基线

- Java: 21
- Maven: 3.9.9+
- Spring Boot: 3.3.2
- ORM: MyBatis-Plus
- 工程形态: 单仓多模块 Maven 模块化单体

## 模块

- `whut-eval-app`: 启动装配
- `whut-eval-common`: 通用响应、错误码、异常基础设施
- `whut-eval-domain`: 领域模型与 Repo 接口
- `whut-eval-application`: 用例编排层
- `whut-eval-infra`: MyBatis-Plus、Redis、配置适配与 Repo 实现
- `whut-eval-interfaces`: HTTP 接口与全局异常处理

## 模块依赖

推荐按以下顺序理解和开发：

1. `whut-eval-common`
2. `whut-eval-domain`
3. `whut-eval-infra`
4. `whut-eval-application`
5. `whut-eval-interfaces`
6. `whut-eval-app`

依赖原则：

- `common` 是最底层共享模块
- `domain` 依赖 `common`
- `infra` 依赖 `common + domain`
- `application` 依赖 `common + domain`
- `interfaces` 依赖 `common + application`
- `app` 负责组装全部模块并生成最终部署包

## 文档

- 重写项目总览：`../docs/index.md`
- 模块文档目录：`../docs/`
- HTTP 接口总表与分组说明：`docs/reference/api-surface.md`
- 学生申请写接口入口：`docs/reference/api-surface.md` 中 `Student Write 接口 / StudentApplicationSubmissionController`
- 学生申请草稿创建接口：`POST /api/student/applications/drafts`
- 学生申请草稿更新接口：`PUT /api/student/applications/{applicationId}/draft`
- 学生申请提交接口：`POST /api/student/applications/{applicationId}/submit`
- 学生申请撤回接口：`POST /api/student/applications/{applicationId}/withdraw`
- 对象存储配置、上传接口与验收说明：`docs/reference/object-storage-config.md`
- 附件存储、公共附件池与申请绑定 SQL 蓝图：`docs/reference/attachment-storage-sql-blueprint.md`
- `P0-2` 最小写入参考实现：`docs/reference/api-surface.md` 中 `Student Write 接口 / StudentPreferenceController`
- `ApplicationSubmission` 最小领域模型、命令集合与上传结果接入蓝图：`docs/reference/application-submission-blueprint.md`
- 认证、权限与可见范围链路：`docs/reference/auth-login-permission-scope-flow.md`
- 认证、权限与可见范围时序图评审版：`docs/reference/auth-login-permission-scope-sequence-review.md`
- Scope Rule 表关系与 SQL 翻译参考：`docs/reference/scope-rule-schema-sql-reference.md`
- student 权限与 SELF 规则一键初始化脚本：`whut-eval-app/src/main/resources/sql/iam/student-self-bootstrap.sql`
- student 角色 self 权限初始化脚本：`whut-eval-app/src/main/resources/sql/iam/student-self-permissions-init.sql`
- student 角色 SELF 范围规则初始化脚本：`whut-eval-app/src/main/resources/sql/iam/student-self-scope-rules-init.sql`
- 推荐先阅读：
  - `../docs/whut-eval-common/index.md`
  - `../docs/whut-eval-domain/index.md`
  - `../docs/whut-eval-infra/index.md`
