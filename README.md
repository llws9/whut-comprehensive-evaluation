# whut-comprehensive-evaluation

武汉理工大学综测平台后端重写工程。该仓库采用 DDD 风格的多模块 Maven 模块化单体，聚焦身份认证、权限与范围控制、学生申请、审核流转、成绩汇总、附件上传与平台治理等核心能力。

## 项目概览

- 技术基线：Java 21、Maven 3.9.9+、Spring Boot 3.3.2、MyBatis-Plus
- 工程形态：单仓多模块 Maven 项目
- 统一响应：`ApiResponse<T>`
- 统一异常：由 `GlobalExceptionHandler` 负责映射
- 默认端口：`8080`

当前仓库重点覆盖以下能力：

- 认证与授权：登录、刷新 Token、权限码、范围规则
- 学生侧能力：申请草稿创建、修改、提交、撤回，个人偏好设置
- 查询能力：学生/管理员查询、成绩查询、身份信息查询
- 附件能力：文件上传、`fileId` 回传、申请附件绑定、公共附件池蓝图
- 平台能力：Nacos typed config、JWT 安全配置、日志与异常约束

## 模块结构

| 模块 | 职责 |
|---|---|
| `whut-eval-common` | 通用响应、错误码、异常、日志基础设施 |
| `whut-eval-domain` | 领域模型、仓储接口、领域服务抽象 |
| `whut-eval-application` | 用例编排、查询/命令服务、应用层 DTO |
| `whut-eval-infra` | MyBatis-Plus、Nacos、JWT、Redis、OSS、仓储实现 |
| `whut-eval-interfaces` | HTTP Controller、请求模型、返回模型、全局异常处理 |
| `whut-eval-app` | Spring Boot 启动装配与最终运行入口 |

推荐阅读顺序：

1. `whut-eval-common`
2. `whut-eval-domain`
3. `whut-eval-application`
4. `whut-eval-infra`
5. `whut-eval-interfaces`
6. `whut-eval-app`

依赖原则：

- `common` 为基础共享模块
- `domain` 仅依赖 `common`
- `application` 依赖 `common + domain`
- `infra` 依赖 `common + domain`
- `interfaces` 依赖 `common + application`
- `app` 负责装配全部模块并输出可运行应用

## 快速开始

### 1. 环境要求

- JDK 21
- Maven 3.9.9 或更高版本
- 可用的 Nacos 服务，默认地址 `127.0.0.1:8848`

### 2. 编译

```bash
mvn clean compile
```

### 3. 测试

```bash
mvn test
```

说明：

- 仓库包含较多单元测试与集成测试
- 执行前请确认外部依赖可达，避免把环境问题误判为代码问题

### 4. 启动应用

从仓库根目录执行：

```bash
mvn -pl whut-eval-app -am spring-boot:run
```

应用主入口位于 `whut-eval-app` 模块，默认监听 `8080` 端口。

## 启动前配置

当前应用默认启用了 JWT 与 Nacos typed config，启动前需要重点确认以下配置：

- JWT 密钥：环境变量 `WHUT_EVAL_JWT_SECRET`
- Nacos 地址：`infra.nacos.connection.server-address`
- Nacos 命名空间：`infra.nacos.connection.namespace`
- 必需配置集：
  - `whut-eval-shared.yaml`
  - `whut-eval-platform-rules.yaml`
  - `whut-eval-oss-storage.yaml`

如果本地没有对应的 Nacos 配置集，应用可能无法完整启动。默认配置位于：

- `whut-eval-app/src/main/resources/application.yml`

当前公开接口白名单包括：

- `/actuator/health`
- `/actuator/info`
- `/api/public/**`
- `/api/auth/login`
- `/api/auth/refresh`
- `/swagger-ui/**`
- `/v3/api-docs/**`

## 常用接口入口

以下接口是当前仓库中最关键的业务入口：

| 分组 | 路由 | 说明 |
|---|---|---|
| 认证 | `POST /api/auth/login` | 登录 |
| 认证 | `POST /api/auth/refresh` | 刷新 Token |
| 学生申请 | `POST /api/student/applications/drafts` | 创建申请草稿 |
| 学生申请 | `PUT /api/student/applications/{applicationId}/draft` | 更新申请草稿 |
| 学生申请 | `POST /api/student/applications/{applicationId}/submit` | 提交申请 |
| 学生申请 | `POST /api/student/applications/{applicationId}/withdraw` | 撤回申请 |
| 文件上传 | `POST /api/files/upload` | 上传文件并返回 `fileId` |

完整接口说明请查看 `docs/reference/api-surface.md`。

## 文档导航

### 参考文档

- `docs/reference/api-surface.md`：HTTP 接口总表与响应模型附录
- `docs/reference/object-storage-config.md`：对象存储配置、上传链路与验收说明
- `docs/reference/attachment-storage-sql-blueprint.md`：`file_asset`、`public_attachment_entry`、`application_attachment` 三表蓝图
- `docs/reference/application-submission-blueprint.md`：申请写链路最小蓝图
- `docs/reference/auth-login-permission-scope-flow.md`：认证、权限与可见范围链路说明
- `docs/reference/auth-login-permission-scope-sequence-review.md`：认证链路时序图评审版
- `docs/reference/scope-rule-schema-sql-reference.md`：Scope Rule 表关系与 SQL 翻译参考

### 团队交付文档

- `docs/team-delivery/README.md`：5 组并行开发文档入口
- `docs/team-delivery/delivery-master-checklist.md`：总控交付清单
- `docs/team-delivery/database-schema-confirmation.md`：数据库冻结说明
- `docs/team-delivery/database-frozen-tables.md`：19 张核心表清单
- `docs/team-delivery/database-table-ownership-matrix.md`：数据库表责任矩阵
- `docs/team-delivery/foundation-capabilities-guide.md`：认证、文件、配置、日志等底座能力说明

### 初始化脚本

- `whut-eval-app/src/main/resources/sql/iam/student-self-bootstrap.sql`
- `whut-eval-app/src/main/resources/sql/iam/student-self-permissions-init.sql`
- `whut-eval-app/src/main/resources/sql/iam/student-self-scope-rules-init.sql`

## 开发约定

- 控制器层负责请求解析、鉴权注解和响应封装，业务判断下沉到应用层或领域层
- 数据访问统一通过仓储与 Mapper 实现，不在接口层直接处理 SQL
- 文件能力统一围绕 `fileId` 交互，不向业务请求暴露底层存储细节
- 新增或修改鉴权逻辑时，同时检查权限码、范围规则和资源访问上下文
- 不在代码中提交真实密钥、令牌、数据库口令或对象存储凭据

## 当前状态

仓库已完成基础模块骨架、认证链路、部分学生写接口、文件上传与附件绑定蓝图、团队交付文档与接口文档整理。后续开发建议优先按 `docs/team-delivery/` 中的总控清单推进。
