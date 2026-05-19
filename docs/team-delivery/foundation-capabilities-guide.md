# 底座能力与开发规范说明

## 1. 文档目的

本文档面向 5 个开发组，统一回答以下问题：

1. 当前 rewrite 工程有哪些已经可复用的底座能力。
2. 业务模块应该怎么调用认证、仓储、文件上传、配置、日志等能力。
3. 开发时哪些事情可以做，哪些事情不应该做。

这份文档的目标不是讲业务，而是防止每组重复造底座或越过分层边界直接写“快但脏”的代码。

## 2. 工程分层总览

当前 rewrite 工程是“单仓多模块 Maven 模块化单体”，主要模块如下：

- `whut-eval-common`：通用响应、错误码、异常、日志工具
- `whut-eval-domain`：领域模型、值对象、仓储接口
- `whut-eval-application`：用例编排层
- `whut-eval-infra`：DB、Redis、Nacos、OSS、安全等技术实现
- `whut-eval-interfaces`：HTTP 接口与全局异常处理
- `whut-eval-app`：启动与装配

推荐依赖方向：

```text
interfaces -> application -> domain
infra      -> domain
app        -> common/domain/application/infra/interfaces
```

## 3. 统一开发原则

- 控制器只负责 HTTP 入参、校验、结果封装，不写业务编排。
- application 层只编排流程，不直接调用 Mapper，不直接写 SQL。
- domain 层只表达业务语义，不感知 MyBatis、Redis、Nacos SDK。
- infra 层只做技术实现，不把 SDK 异常和底层对象泄漏给上层。
- app 模块只做装配，不新增业务 controller/service。

## 4. 认证与授权能力

## 4.1 已有能力

当前已经落地的认证授权底座包括：

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/security/me`
- `UserAuthorizationContextAssembler`
- 运行时权限补齐
- 运行时范围规则补齐
- `AuthorizationScopeEvaluator`
- `ScopePredicateBuilder`
- `ApplicationScopeSqlTranslator` / `ScoreScopeSqlTranslator`

## 4.2 业务代码如何读取当前用户

业务代码不要自己解析 JWT，不要自己从 Header 读用户编号。统一方式：

1. 在 application service 中注入 `UserAuthorizationContextAssembler`
2. 调用 `requiredAuthorizationContext()` 获取当前用户
3. 读取 `userId/authorities/scopeRules`

推荐用法：

```java
UserAuthorizationContext context = userAuthorizationContextAssembler.requiredAuthorizationContext();
Long currentUserId = context.getUserId();
```

完整示例：

```java
@Service
public class StudentApplicationFacade {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final ApplicationSubmissionRepository applicationSubmissionRepository;

    public StudentApplicationFacade(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                    ApplicationSubmissionRepository applicationSubmissionRepository) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.applicationSubmissionRepository = applicationSubmissionRepository;
    }

    public ApplicationSubmissionView loadOwnedApplication(Long applicationId) {
        UserAuthorizationContext context = userAuthorizationContextAssembler.requiredAuthorizationContext();
        ApplicationSubmission submission = applicationSubmissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在"));
        if (!context.getUserId().equals(submission.getApplicantUserId())) {
            throw new ValidationException("当前用户无权操作该申请");
        }
        return new ApplicationSubmissionView(
                submission.getApplicationId(),
                submission.getStatus(),
                submission.getTitle(),
                submission.getDescription(),
                submission.getEvidenceAttachments().size(),
                submission.getVersion()
        );
    }
}
```

不推荐：

- 直接在 controller 里解析 token
- 业务 service 自己查 Redis 拼用户上下文
- 各组重复定义“当前用户工具类”

## 4.3 权限校验怎么做

- 只校验动作权限：优先用 `@PreAuthorize` 或统一权限常量
- 需要数据范围：通过 A 组提供的范围评估器/谓词构造器做校验
- 列表查询：通过 `ScopePredicateBuilder + SqlTranslator` 转为查询条件
- 单条资源访问：通过 `ResourceScopeAccessEvaluator` 校验

控制器最小示例：

```java
@RestController
@RequestMapping("/api/student/query")
public class StudentQueryController {

    private final ApplicationQueryApplicationService applicationQueryApplicationService;

    public StudentQueryController(ApplicationQueryApplicationService applicationQueryApplicationService) {
        this.applicationQueryApplicationService = applicationQueryApplicationService;
    }

    @PreAuthorize("hasAuthority('application.view.self')")
    @GetMapping("/applications")
    public ApiResponse<PageResult<ApplicationRecordView>> pageApplications(@Valid ApplicationPageRequest request) {
        return ApiResponse.success(applicationQueryApplicationService.pageSelfApplications(request.toQuery()));
    }
}
```

## 5. 仓储调用说明

这里的“仓储调用”统一指 Domain Repo / Query Repo 的调用方式，不是直接调 MyBatis Mapper。

## 5.1 正确链路

```text
Controller
  -> ApplicationService
    -> Domain Repository / Query Repository
      -> Infra Repository 实现
        -> Mapper / XML / DB
```

## 5.2 为什么不能直接在 application 层调 Mapper

因为一旦 application 层直接调 Mapper，会出现：

- SQL 与业务流程耦合
- 测试困难
- 缓存、审计、事务边界难统一
- 多组并行时容易复制粘贴同一套 SQL

## 5.3 推荐做法

- 先在 `domain` 定义仓储接口，例如：`ApplicationSubmissionRepository`
- 再在 `infra` 实现，例如：`MybatisPlusApplicationSubmissionRepository`
- application 层只依赖接口，不依赖实现类

推荐示例：

```java
public interface ApplicationSubmissionRepository {

    Optional<ApplicationSubmission> findById(Long applicationId);

    ApplicationSubmission save(ApplicationSubmission submission);
}
```

```java
@Service
public class ApplicationSubmissionCommandApplicationService {

    private final ApplicationSubmissionRepository applicationSubmissionRepository;

    public ApplicationSubmissionCommandApplicationService(
            ApplicationSubmissionRepository applicationSubmissionRepository) {
        this.applicationSubmissionRepository = applicationSubmissionRepository;
    }

    @Transactional
    public ApplicationSubmissionView submit(SubmitApplicationCommand command) {
        ApplicationSubmission submission = applicationSubmissionRepository.findById(command.getApplicationId())
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在"));
        ApplicationSubmission saved = applicationSubmissionRepository.save(
                submission.submit(command.getExpectedVersion())
        );
        return new ApplicationSubmissionView(
                saved.getApplicationId(),
                saved.getStatus(),
                saved.getTitle(),
                saved.getDescription(),
                saved.getEvidenceAttachments().size(),
                saved.getVersion()
        );
    }
}
```

## 5.4 查询仓储与命令仓储建议分开

- 列表/详情：Query Repository
- 写入/状态流转：Command Repository 或聚合 Repository
- 范围过滤：Query Repository 内部消费范围谓词，而不是 controller 拼 SQL

## 6. 文件上传能力

## 6.1 已有能力

当前文件上传链路已经支持：

- `POST /api/files/upload`
- OSS 上传
- `file_asset` 登记
- 返回稳定 `fileId`
- 学生申请通过 `attachmentFileIds` 绑定附件
- 统一失败语义与日志事件

## 6.2 业务方怎么接入

业务模块统一按“两段式”接入：

1. 前端先调 `/api/files/upload`
2. 拿到 `fileId`
3. 在正式业务写接口中只传 `attachmentFileIds`
4. 服务端通过 `ApplicationAttachmentResolver` 等组件校验并反查元数据

不允许：

- 正式业务请求体直接传 `storageKey`
- 让前端传 `uploadedBy`
- 让业务模块自己拼 OSS key

接口契约示例：

```json
{
  "orgUnitId": 10001,
  "categoryCode": "MORAL",
  "itemCode": "LECTURE_ATTENDANCE",
  "academicYear": "2025-2026",
  "term": "1",
  "title": "参加学院讲座",
  "description": "完成 3 次学院讲座签到",
  "attachmentFileIds": ["file_a123", "file_b456"]
}
```

服务端解析示例：

```java
private List<AttachmentRef> resolveAttachments(List<String> attachmentFileIds, Long currentUserId) {
    if (attachmentFileIds == null || attachmentFileIds.isEmpty()) {
        return List.of();
    }
    return applicationAttachmentResolver.resolveForBinding(attachmentFileIds, currentUserId);
}
```

## 6.3 失败语义必须复用

上传模块和业务模块必须复用已定义错误码：

- `VAL-4001`：空文件、附件不存在、附件无权使用
- `CFG-5031`：配置缺失
- `EXT-5033`：对象存储失败
- `BIZ-4090`：重复附件、版本冲突

## 7. Nacos 配置新增说明

## 7.1 已有能力

infra 已完成 Nacos typed config 基础能力，包括：

- `ConfigDefinition`
- `ConfigBootstrapInitializer`
- `TypedConfigBinding`
- `TypedConfigRepository`
- `TypedConfigMaterializer`
- 启动预热与自动刷新

## 7.2 新增一个配置怎么做

推荐步骤：

1. 在 `whut-eval-app/src/main/resources/application.yml` 注册 `definition`
2. 在 Nacos 创建对应 `dataId`
3. 在 `infra/nacos/model/typed/` 新增强类型类
4. 在 `NacosTypedConfigConfiguration` 注册 binding
5. 业务代码通过 `TypedConfigRepository` 读取

示例定义名：`student-submit-rule-config`

配置模型示例：

```java
public class StudentSubmitRuleConfig {

    private boolean studentApplyEnabled;
    private boolean finalSubmitEnabled;
    private String deadlineAt;

    public boolean isStudentApplyEnabled() {
        return studentApplyEnabled;
    }

    public void setStudentApplyEnabled(boolean studentApplyEnabled) {
        this.studentApplyEnabled = studentApplyEnabled;
    }

    public boolean isFinalSubmitEnabled() {
        return finalSubmitEnabled;
    }

    public void setFinalSubmitEnabled(boolean finalSubmitEnabled) {
        this.finalSubmitEnabled = finalSubmitEnabled;
    }

    public String getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(String deadlineAt) {
        this.deadlineAt = deadlineAt;
    }
}
```

## 7.3 业务读取方式

推荐：

```java
typedConfigRepository.find("platform-rule-config", PlatformRuleConfig.class)
```

带校验的读取示例：

```java
StudentSubmitRuleConfig config = typedConfigRepository
        .find("student-submit-rule-config", StudentSubmitRuleConfig.class)
        .orElseThrow(() -> new IllegalStateException("student-submit-rule-config 未加载"));

if (!config.isStudentApplyEnabled()) {
    throw new ValidationException("当前申请窗口未开放");
}
```

不推荐：

- 业务层直接注入 Nacos `ConfigService`
- 业务层自己解析 YAML
- 业务层拿不到配置时静默兜底随机默认值

## 8. 日志打点规范

## 8.1 统一日志工具

统一使用 `AppLog`，不要自己手拼半结构化日志。

推荐写法：

```java
AppLog.info(log, "file.upload.request.received", "userId", userId, "bizType", bizType);
AppLog.error(log, ex, "file.upload.oss.put-object.failed", "bucket", bucket);
```

应用服务示例：

```java
@Service
public class FinalRecordCommandService {

    private static final Logger log = LoggerFactory.getLogger(FinalRecordCommandService.class);

    public void confirm(Long recordId, Long operatorUserId) {
        AppLog.info(log, "final.record.confirm.requested",
                "recordId", recordId,
                "operatorUserId", operatorUserId);
        try {
            // do confirm
            AppLog.info(log, "final.record.confirm.completed",
                    "recordId", recordId,
                    "operatorUserId", operatorUserId,
                    "status", "CONFIRMED");
        } catch (RuntimeException ex) {
            AppLog.error(log, ex, "final.record.confirm.failed",
                    "recordId", recordId,
                    "operatorUserId", operatorUserId);
            throw ex;
        }
    }
}
```

## 8.2 日志字段建议

每条关键日志尽量包含：

- `event`
- `userId`
- `applicationId` / `fileId` / `recordId` 等业务主键
- `categoryCode`
- `itemCode`
- `status`
- `elapsedMs`（如果是耗时动作）

## 8.3 必须打日志的场景

- 登录成功/失败
- 范围规则加载失败
- 文件上传请求、拒绝、完成、失败
- 导入开始、完成、失败、失败行统计
- 批量审批开始、完成、部分失败
- 配置加载失败、配置刷新成功
- AI 报告生成失败

## 9. 异常处理规范

## 9.1 统一异常入口

所有接口异常统一交给 `GlobalExceptionHandler`。

当前已支持：

- `BaseAppException`
- `MethodArgumentNotValidException`
- 未知异常兜底为 `SYS-5000`

## 9.2 业务代码应该抛什么

- 参数问题：`ValidationException`
- 未找到资源：`ResourceNotFoundException`
- 冲突：`ConflictException`
- 配置缺失：对应配置异常

推荐示例：

```java
if (command.getExpectedVersion() == null) {
    throw new ValidationException("expectedVersion 不能为空");
}

ApplicationSubmission submission = applicationSubmissionRepository.findById(command.getApplicationId())
        .orElseThrow(() -> new ResourceNotFoundException("申请不存在"));

if (!currentUserId.equals(submission.getApplicantUserId())) {
    throw new ValidationException("当前用户无权操作该申请");
}

if (activeSubmissionPolicy.hasActiveSubmission(
        currentUserId,
        command.getItemCode(),
        command.getAcademicYear(),
        command.getTerm(),
        null
)) {
    throw new ConflictException("当前项目在该学年学期下已存在活跃申请");
}
```

不要做：

- `printStackTrace()`
- `throw new RuntimeException("业务错误")`
- controller 手工 `try/catch` 后返回拼接字符串

## 10. 接口设计规范

- 所有正式接口统一前缀为 `/api/...`
- 能 `GET` 就不要 `POST` 查询
- 分页接口统一使用 `pageNo/pageSize`
- 批量接口返回成功数/失败数/失败明细
- 重要写接口带 `expectedVersion`
- 文件流导出接口明确 `Content-Type` 与文件名规则

## 11. 数据与命名规范

- 统一使用 `categoryCode + itemCode`，不要继续滥用旧系统 `application_id`
- 统一使用 `fileId`，不要让业务层长期依赖 `storageKey`
- 统一使用 `applicationId` 作为申请主键对外口径
- 状态枚举必须在文档和代码中保持同一套命名

## 12. 测试与验收规范

- 关键写链路至少补 application service test + WebMvc test
- 范围过滤至少补一条“有权限”和一条“越权拦截”测试
- 上传链路至少补空文件、配置缺失、存储失败三类异常测试
- 导入链路至少补格式错误、失败行回执测试
- 大改前先确认外部依赖是否可达，避免把环境问题误判成代码问题

## 13. 各组协作硬约束

- A 组冻结 `permissionCode`、`roleCode`、`scopeType`
- B/C/D/E 组不能自行发明新的认证上下文读取方式
- B/E 组冻结 `attachmentFileIds` 契约
- C/D 组冻结 `APPROVED/RETURNED/REJECTED` 与最终汇总关系
- D/E 组不得私自恢复导入批次表或运行时配置表；若二期需要异步任务或配置落库，必须单独评审

## 14. 动态指标配置能力

### 14.1 概述

动态指标配置是评估系统的核心底座能力，支持通过 Nacos 配置中心动态管理四大类（德育、智育、体育与美育、劳育）的测评指标、评分标准、最高分值和资格规则。

### 14.2 配置文件结构

系统包含三个核心配置文件：

| 配置文件 | Data ID | 说明 |
|---------|---------|------|
| 测评指标 | `whut-eval-evaluation-items.yaml` | 定义指标基本信息、最高分值、申请方式 |
| 指标选项 | `whut-eval-index-options.yaml` | 定义各指标的评分标准选项 |
| 资格规则 | `whut-eval-eligibility-rules.yaml` | 定义参评学业奖学金的基本要求 |

### 14.3 测评指标配置

```yaml
evaluation-items:
  MORAL:
    - itemCode: "MORAL_REWARD_PUNISHMENT"
      itemName: "奖惩"
      maxPoints: 6                              # 固定最高分值
      maxPointsExpression: "isPartyMember ? 8 : 6"  # SpEL动态计算
      applyMode: "STUDENT_APPLY"                # STUDENT_APPLY/SYSTEM_CALCULATED/TEACHER_IMPORT
      optionsKey: "moral-reward-punishment"     # 关联选项key
```

### 14.4 指标选项配置

```yaml
index-options:
  # 普通选项（固定分值）
  moral-reward-punishment:
    - optionCode: "REWARD_PROVINCIAL"
      optionName: "省级及以上荣誉称号"
      points: 3
      allowCustomPoints: false
  
  # "其他"类别（无固定评分标准，允许自定义分值）
  sports-other:
    - optionCode: "OTHER_CUSTOM"
      optionName: "其他活动"
      points: null                    # 无固定分值
      allowCustomPoints: true         # 允许学生自定义
```

### 14.5 资格规则配置

```yaml
eligibility-rules:
  LABOR:
    - ruleId: "LABOR_RULE_1"
      ruleType: "EXPRESSION"
      description: "党员1.5分及以上，其他学生1分及以上"
      expression: "isPartyMember ? (laborScore >= 1.5) : (laborScore >= 1.0)"
      enabled: true
```

### 14.6 SpEL表达式支持

#### 支持的上下文变量

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `isPartyMember` | boolean | 是否党员 |
| `academicYear` | int | 入学年份 |
| `grade` | string | 年级 |
| `moralScore` | BigDecimal | 德育总分 |
| `intellectualScore` | BigDecimal | 智育总分 |
| `sportsScore` | BigDecimal | 体育与美育总分 |
| `laborScore` | BigDecimal | 劳育总分 |

#### 表达式示例

| 表达式 | 说明 |
|--------|------|
| `"isPartyMember ? 8 : 6"` | 党员最高8分，非党员最高6分 |
| `"academicYear >= 2023 ? 10 : 8"` | 2023级及以后最高10分 |
| `"isPartyMember ? (laborScore >= 1.5) : (laborScore >= 1.0)"` | 党员需1.5分，非党员需1分 |

### 14.7 业务代码调用方式

```java
@Service
public class EvaluationConfigApplicationService {
    
    private final TypedConfigRepository configRepository;
    private final RuleEngineService ruleEngineService;
    
    public EvaluationConfigApplicationService(TypedConfigRepository configRepository,
                                              RuleEngineService ruleEngineService) {
        this.configRepository = configRepository;
        this.ruleEngineService = ruleEngineService;
    }
    
    // 获取指标配置
    public EvaluationItem getEvaluationItem(String itemCode) {
        EvaluationItemsConfig config = configRepository
                .find("evaluation-items-config", EvaluationItemsConfig.class)
                .orElseThrow(() -> new ConfigLoadException("evaluation-items config not found"));
        return findItemByCode(config, itemCode);
    }
    
    // 获取指标选项
    public List<OptionItem> getOptions(String itemCode) {
        EvaluationItem item = getEvaluationItem(itemCode);
        String optionsKey = item.getOptionsKey();
        IndexOptionsConfig config = configRepository
                .find("index-options-config", IndexOptionsConfig.class)
                .orElseThrow(() -> new ConfigLoadException("index-options config not found"));
        return config.getIndexOptions().getOrDefault(optionsKey, List.of());
    }
    
    // 计算最高分值（支持SpEL）
    public BigDecimal calculateMaxPoints(String itemCode, StudentContext context) {
        return ruleEngineService.calculateMaxPoints(itemCode, context);
    }
    
    // 评估资格
    public boolean evaluateEligibility(String categoryCode, StudentEvaluationSummary summary) {
        return ruleEngineService.evaluateEligibility(categoryCode, summary);
    }
}
```

### 14.8 特殊情况处理

#### "其他"类别处理

当 `allowCustomPoints: true` 且 `points: null` 时：
- 前端显示输入框让学生填写申请分值
- 系统仍会检查是否超过该指标的 `maxPoints` 上限

#### 评分档位自动计算

普通评分标准不允许学生手填分值。提交时前端传入 `optionCode`，后端按 `itemCode -> optionsKey -> optionCode` 从 `index-options-config` 读取固定 `points`，并以配置分值作为本次申请分值。

#### 超过最高分值处理

超过最高分值不阻断申请。后端仍保存申请并返回 `exceedsMaxPoints: true`、`appliedPoints`、`maxPoints` 和 `warningMessage`，前端需要在申请成功后展示提示；审核或最终计分阶段按最高分值上限处理。

#### SpEL 解析口径

规则引擎会把学生上下文作为 SpEL root object，同时注入同名变量，配置中既可以写 `isPartyMember ? 8 : 6`，也可以使用 `#isPartyMember` 形式。推荐统一使用变量名直写，保持配置简洁。

#### 无资格要求的指标

不在 `eligibility-rules` 中配置该类别的规则，或配置空规则列表。

## 15. 一句话规则

- 认证从 A 组底座读。
- 仓储经 domain repo 调。
- 文件先上传再绑定。
- 配置通过 typed config 读。
- 日志统一走 `AppLog`。
- 异常统一走全局处理器。
- 指标配置通过动态配置中心管理。
- 不跨层，不重复造轮子。
