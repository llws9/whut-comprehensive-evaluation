# 接口开发示例文档

## 1. 文档目的

这份文档给第一次在本项目里做后端接口开发的同学看，目标不是讲抽象规范，而是直接回答四个问题：

1. 在这个项目里，一个接口到底分成哪些层来写。
2. 用户信息、权限、可见范围是从哪里来的。
3. 查询、写入、缓存、配置、日志、对象存储分别应该落在哪一层。
4. 新人第一次照着做时，应该抄哪几段真实代码。

先说结论：

- 不要试图找一个“包打天下”的接口样例。本项目把能力拆得很清楚，一个真实接口通常只覆盖其中一部分职责。
- 如果你硬把“权限范围 + Redis + Nacos + OSS + DB 写入 + DB 查询”全塞进一个 Controller，这反而不符合本项目分层。
- 正确学习方式是看三条主线，再补一个缓存切面：
  - 查询主线：`GET /api/student/query/applications`
  - 上传主线：`POST /api/files/upload`
  - 写入主线：`POST /api/student/preferences`
  - 缓存切面：`MybatisPlusIamUserQueryRepository + RedisUserCacheGateway`

## 2. 先优化这次提示词

下面这段就是本次需求更适合投给代码助手的版本。

```text
请基于仓库 /Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation 的现有文档和已落地代码，编写一份“接口开发示例文档（给第一次做接口开发的新人）”。

要求：
1. 文档必须贴合当前项目实际，不要写成通用 Spring Boot 教程。
2. 不修改任何现有业务代码文件，只允许新增文档。
3. 文档使用中文，技术名词、代码标识、API 路径、配置键、类名保留原始英文。
4. 文档必须覆盖这些主题，并且凡是提到代码的地方都要给出具体代码块：
   - 接口路由、Controller 定义、权限注解
   - 入参、出参、请求体、响应体
   - 从上下文中获取当前用户、权限、可见范围
   - application service 的职责和实现
   - domain repository 接口
   - infra repository 实现
   - mapper 定义与 SQL 组装
   - 读 DB、写 DB
   - 读 Redis、写 Redis、缓存失效
   - 读 Nacos typed config
   - 日志埋点
   - 对象存储上传与 file_asset 登记
   - 异常与统一返回结构
5. 文档不要伪造本项目不存在的架构。若单个接口无法自然覆盖全部机制，请明确拆成“主线接口 + 补充切面”来讲。

建议写作结构：
1. 先解释本项目接口开发的最小分层模型
2. 以 `GET /api/student/query/applications` 讲“权限 + 范围 + 查询”
3. 以 `POST /api/files/upload` 讲“配置 + OSS + DB + 日志”
4. 以 `POST /api/student/preferences` 讲“请求体校验 + 事务 + DB 写入 + 缓存失效”
5. 再补充 `MybatisPlusIamUserQueryRepository + RedisUserCacheGateway` 作为真实 cache-aside 示例
6. 最后给一个新人开发 checklist

验收标准：
- 新人只读这份文档，就能知道自己该在哪一层写什么。
- 文档中的代码块都能在当前仓库里找到对应实现或明显的同构实现。
- 文档必须明确说明“认证、权限、范围”是三件不同的事。
- 文档必须明确说明“业务过滤条件”和“范围条件”在 SQL 里是 AND 关系。
```

## 3. 本项目里，一个接口的最小分层模型

先把脑子里的链路搭起来：

```text
HTTP Request
  -> Controller
  -> Application Service
  -> Domain Repository Interface
  -> Infra Repository
  -> Mapper / SQL Provider
  -> DB / Redis / Nacos / OSS
  -> ApiResponse
```

这条链路里每层只做自己的事：

- `Controller` 只负责 HTTP 入参解析、注解、返回值包装。
- `Application Service` 负责业务编排，决定调用哪个仓储或外部能力。
- `Domain Repository Interface` 负责稳定边界，不让上层直接碰 MyBatis。
- `Infra Repository` 负责把业务上下文翻译成持久化层能执行的查询或写入。
- `Mapper / SQL Provider` 负责实际 SQL。
- `SecurityContext` 负责当前用户、权限、范围进入运行时上下文。
- `Nacos` 是运行时配置真源，不能把一期规则错写成 DB 真源。

## 4. 示例 A：`GET /api/student/query/applications`

这一条主线最适合讲清楚四件事：

1. 路由和权限注解怎么写。
2. 当前用户、权限、范围从哪里拿。
3. 查询仓储怎么把范围规则翻译成 SQL。
4. 业务过滤条件为什么必须和范围条件做 `AND`。

### 4.1 HTTP 入口

请求示例：

```bash
curl -X GET 'http://localhost:8080/api/student/query/applications?pageNo=1&pageSize=20&categoryCode=MORAL' \
  -H 'Authorization: Bearer <access-token>'
```

典型响应：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {
    "total": 2,
    "records": [
      {
        "applicationId": 1001,
        "applicantUserId": 2001,
        "orgUnitId": 3001,
        "orgPath": "/school/college-a/class-1",
        "categoryCode": "MORAL",
        "itemCode": "MORAL_01"
      }
    ]
  }
}
```

### 4.2 Controller 怎么写

这里先看真实 Controller。重点看三件事：

- 路由前缀是 `/api/student/query`
- 方法路由是 `/applications`
- 权限注解显式写 `application.view.self`

```java
@RestController
@RequestMapping("/api/student/query")
@Validated
public class StudentQueryController {

    private final ApplicationQueryApplicationService applicationQueryApplicationService;
    private final ScoreQueryApplicationService scoreQueryApplicationService;

    public StudentQueryController(ApplicationQueryApplicationService applicationQueryApplicationService,
                                  ScoreQueryApplicationService scoreQueryApplicationService) {
        this.applicationQueryApplicationService = applicationQueryApplicationService;
        this.scoreQueryApplicationService = scoreQueryApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_VIEW_SELF)")
    @GetMapping("/applications")
    public ApiResponse<PageResult<ApplicationRecordView>> pageApplications(@RequestParam(defaultValue = "1") long pageNo,
                                                                           @RequestParam(defaultValue = "20") long pageSize,
                                                                           @RequestParam(required = false) Long applicationId,
                                                                           @RequestParam(required = false) Long applicantUserId,
                                                                           @RequestParam(required = false) Long orgUnitId,
                                                                           @RequestParam(required = false) String categoryCode,
                                                                           @RequestParam(required = false) String itemCode) {
        return ApiResponse.success(applicationQueryApplicationService.pageAccessibleApplications(
                new ApplicationPageQuery(pageNo, pageSize, applicationId, applicantUserId, orgUnitId, categoryCode, itemCode),
                AuthorizationPermissionCodes.APPLICATION_VIEW_SELF
        ));
    }
}
```

这段代码说明：

- HTTP 参数到 `ApplicationPageQuery` 的组装发生在 Controller。
- 权限注解发生在 Controller。
- 真正的业务编排下沉到 `ApplicationQueryApplicationService`。
- Controller 不直接写 SQL，不直接读 `SecurityContextHolder`，不直接碰 Mapper。

### 4.3 入参对象怎么定义

查询接口虽然没有 `@RequestBody`，但仍然应该把业务过滤条件收敛成一个对象。真实代码如下：

```java
public class ApplicationPageQuery {

    private final long pageNo;
    private final long pageSize;
    private final Long applicationId;
    private final Long applicantUserId;
    private final Long orgUnitId;
    private final String categoryCode;
    private final String itemCode;

    public ApplicationPageQuery(long pageNo,
                                long pageSize,
                                Long applicationId,
                                Long applicantUserId,
                                Long orgUnitId,
                                String categoryCode,
                                String itemCode) {
        if (pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be greater than 0");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be greater than 0");
        }
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.applicationId = applicationId;
        this.applicantUserId = applicantUserId;
        this.orgUnitId = orgUnitId;
        this.categoryCode = categoryCode;
        this.itemCode = itemCode;
    }

    public long getOffset() {
        return (pageNo - 1) * pageSize;
    }
}
```

这里有两个要点：

- 参数合法性先在对象构造阶段兜住。
- `offset` 是查询对象自己的职责，不要散落在 Mapper 调用处手算。

### 4.4 出参对象怎么定义

对外视图不要直接把数据库 DO 暴露出去。真实代码如下：

```java
public class ApplicationRecordView {

    private final Long applicationId;
    private final Long applicantUserId;
    private final Long orgUnitId;
    private final String orgPath;
    private final String categoryCode;
    private final String itemCode;

    public ApplicationRecordView(Long applicationId,
                                 Long applicantUserId,
                                 Long orgUnitId,
                                 String orgPath,
                                 String categoryCode,
                                 String itemCode) {
        this.applicationId = applicationId;
        this.applicantUserId = applicantUserId;
        this.orgUnitId = orgUnitId;
        this.orgPath = orgPath;
        this.categoryCode = categoryCode;
        this.itemCode = itemCode;
    }
}
```

分页壳也是项目统一对象：

```java
public record PageResult<T>(long total, List<T> records) {
}
```

统一响应壳是：

```java
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", "success", data);
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.code(), message, null);
    }
}
```

### 4.5 Application Service 怎么写

应用服务在这个项目里不是“薄薄一层转发”，它至少做三件事：

1. 从上下文拿当前用户。
2. 显式做权限兜底。
3. 把运行时授权上下文转换成仓储可消费的访问上下文。

真实代码如下：

```java
@Service
public class ApplicationQueryApplicationService {
    static final String DEFAULT_APPLICATION_QUERY_PERMISSION = AuthorizationPermissionCodes.APPLICATION_REVIEW;

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final ApplicationQueryRepository applicationQueryRepository;

    public ApplicationQueryApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                              ApplicationQueryRepository applicationQueryRepository) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.applicationQueryRepository = applicationQueryRepository;
    }

    public PageResult<ApplicationRecordView> pageAccessibleApplications(ApplicationPageQuery query, String permissionCode) {
        UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
        ensurePermissionGranted(authorizationContext, permissionCode);
        PageResult<ApplicationRecord> pageResult = applicationQueryRepository.pageAccessibleApplications(
                toAccessContext(authorizationContext, permissionCode),
                query
        );
        return new PageResult<>(
                pageResult.total(),
                pageResult.records().stream().map(this::toView).toList()
        );
    }

    private void ensurePermissionGranted(UserAuthorizationContext authorizationContext, String permissionCode) {
        if (!authorizationContext.hasAuthority(permissionCode)) {
            throw new AccessDeniedAppException("当前用户无权限访问申请列表");
        }
    }

    private ApplicationAccessContext toAccessContext(UserAuthorizationContext authorizationContext, String permissionCode) {
        return new ApplicationAccessContext(
                authorizationContext.getUserId(),
                authorizationContext.getUserNo(),
                authorizationContext.getUserName(),
                authorizationContext.getIdentity(),
                authorizationContext.getRoles(),
                authorizationContext.getAuthorities(),
                authorizationContext.getScopeRules(),
                permissionCode
        );
    }
}
```

这里一定要记住：

- `Authentication` 是“当前请求是不是登录用户”。
- `Authorization` 是“当前用户有没有这个 `permissionCode`”。
- `Scope` 是“即使有这个权限，当前用户能看哪一部分数据”。

这三件事不是一件事。

### 4.6 当前用户、权限、范围从哪里来

这部分是新人最容易看糊涂的地方。项目里真实链路如下：

```text
JWT -> JwtAuthenticationFilter -> UserAuthorizationContextLoader -> CurrentUser -> SecurityContext -> UserAuthorizationContextAssembler
```

#### 4.6.1 业务层统一取上下文的接口

```java
public interface UserAuthorizationContextAssembler {

    Optional<UserAuthorizationContext> currentAuthorizationContext();

    default UserAuthorizationContext requiredAuthorizationContext() {
        return currentAuthorizationContext().orElseThrow(AuthenticationFailedException::new);
    }
}
```

#### 4.6.2 `SecurityContext` 适配为业务上下文

```java
@Component
public class SecurityContextUserAuthorizationContextAssembler implements UserAuthorizationContextAssembler {

    private final CurrentUserProvider currentUserProvider;

    public SecurityContextUserAuthorizationContextAssembler(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public Optional<UserAuthorizationContext> currentAuthorizationContext() {
        return currentUserProvider.currentUser().map(this::toAuthorizationContext);
    }

    private UserAuthorizationContext toAuthorizationContext(CurrentUser currentUser) {
        return new UserAuthorizationContext(
                currentUser.getUserId(),
                currentUser.getUserNo(),
                currentUser.getUserName(),
                currentUser.getIdentity(),
                currentUser.getRoles(),
                currentUser.getAuthorities(),
                currentUser.getScopeRules()
        );
    }
}
```

#### 4.6.3 运行时主体长什么样

```java
public class CurrentUser {

    private final Long userId;
    private final String userNo;
    private final String userName;
    private final String identity;
    private final Set<String> roles;
    private final Set<String> authorities;
    private final List<IamScopeRule> scopeRules;

    public CurrentUser(Long userId,
                       String userNo,
                       String userName,
                       String identity,
                       Set<String> roles,
                       Set<String> authorities,
                       List<IamScopeRule> scopeRules) {
        this.userId = userId;
        this.userNo = userNo;
        this.userName = userName;
        this.identity = identity;
        this.roles = roles == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(roles));
        this.authorities = authorities == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(authorities));
        this.scopeRules = scopeRules == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(scopeRules));
    }
}
```

#### 4.6.4 JWT Filter 如何把上下文放进去

这里最关键的是：不是只信 token 里的老数据，而是按 `userId` 重新加载授权上下文。

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AppLog.debug(log, "security.jwt.filter.started",
                "path", request.getRequestURI(),
                "method", request.getMethod());

        ResolvedToken resolvedToken = jwtTokenResolver.resolve(request).orElse(null);
        if (resolvedToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Claims claims = jwtClaimsParser.parse(resolvedToken.getToken(), resolvedToken.getSource());
        CurrentUser tokenUser = jwtClaimsToCurrentUserMapper.map(claims);
        UserAuthorizationContext authorizationContext = userAuthorizationContextLoader.load(
                new UserAuthorizationContextLoadRequest(
                        tokenUser.getUserId(),
                        tokenUser.getUserNo(),
                        tokenUser.getUserName(),
                        tokenUser.getIdentity(),
                        tokenUser.getRoles()
                )
        );
        CurrentUser currentUser = toCurrentUser(authorizationContext);
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        currentUser,
                        resolvedToken.getToken(),
                        toGrantedAuthorities(currentUser)
                );

        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        filterChain.doFilter(request, response);
    }
}
```

#### 4.6.5 业务层真正消费的上下文对象

```java
public class UserAuthorizationContext {

    private final Long userId;
    private final String userNo;
    private final String userName;
    private final String identity;
    private final Set<String> roles;
    private final Set<String> authorities;
    private final List<IamScopeRule> scopeRules;

    public boolean hasAuthority(String authorityCode) {
        return authorityCode != null && authorities.contains(authorityCode);
    }

    public List<IamScopeRule> findScopeRulesByPermissionCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return List.of();
        }
        return scopeRules.stream()
                .filter(rule -> permissionCode.equals(rule.permissionCode()))
                .toList();
    }
}
```

#### 4.6.6 调试当前用户最直接的接口

如果你不确定上下文里到底有什么字段，直接看这个探针接口：

```java
@RestController
@RequestMapping("/api/security")
public class SecurityProbeController {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> currentUser() {
        UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", authorizationContext.getUserId());
        payload.put("userNo", authorizationContext.getUserNo());
        payload.put("userName", authorizationContext.getUserName());
        payload.put("identity", authorizationContext.getIdentity());
        payload.put("roles", authorizationContext.getRoles());
        payload.put("authorities", authorizationContext.getAuthorities());
        payload.put("scopeRules", authorizationContext.getScopeRules());
        return ApiResponse.success(payload);
    }
}
```

### 4.7 Repository 接口怎么定义

应用服务依赖的是领域仓储接口，而不是 Mapper。真实代码如下：

```java
public interface ApplicationQueryRepository {

    PageResult<ApplicationRecord> pageAccessibleApplications(ApplicationAccessContext accessContext,
                                                             ApplicationPageQuery query);
}
```

这层边界的意义是：

- 上层不知道 MyBatis 的存在。
- 上层不用关心 `scopeRules` 最终怎么变成 SQL。
- 上层只表达“在某个访问上下文下查可见申请”。

### 4.8 Infra Repository 怎么把范围变成查询

这一层是查询链路最关键的闭环。真实代码如下：

```java
@Repository
public class MybatisPlusApplicationQueryRepository implements ApplicationQueryRepository {

    private final AuthorizationScopeEvaluator authorizationScopeEvaluator;
    private final ScopePredicateBuilder scopePredicateBuilder;
    private final ApplicationScopeSqlTranslator applicationScopeSqlTranslator;
    private final ApplicationQueryMapper applicationQueryMapper;

    @Override
    public PageResult<ApplicationRecord> pageAccessibleApplications(ApplicationAccessContext accessContext,
                                                                    ApplicationPageQuery query) {
        UserAuthorizationContext authorizationContext = toAuthorizationContext(accessContext);
        AuthorizationScopeSet scopeSet = authorizationScopeEvaluator.evaluate(
                authorizationContext,
                accessContext.getPermissionCode()
        );
        ApplicationScopePredicate predicate = scopePredicateBuilder.buildForApplication(authorizationContext, scopeSet);
        SqlPredicateFragment fragment = applicationScopeSqlTranslator.translate(authorizationContext, predicate);

        long total = applicationQueryMapper.countAccessibleApplications(
                fragment.getExpression(),
                fragment.getParameters(),
                query
        );
        List<ApplicationRecord> records = applicationQueryMapper.selectAccessibleApplications(
                        fragment.getExpression(),
                        fragment.getParameters(),
                        query,
                        query.getOffset(),
                        query.getPageSize()
                ).stream()
                .map(this::toDomain)
                .toList();
        return new PageResult<>(total, records);
    }
}
```

这个方法可以直接当模板理解：

- `evaluate`：算出当前权限下可用的范围集合。
- `buildForApplication`：把范围规则转成申请查询谓词。
- `translate`：把谓词转成参数化 SQL 片段。
- `mapper`：真正执行 count 和 select。

### 4.9 Mapper 怎么定义

真实代码如下：

```java
@Mapper
public interface ApplicationQueryMapper {

    @SelectProvider(type = ApplicationQuerySqlProvider.class, method = "buildCountAccessibleApplications")
    long countAccessibleApplications(@Param("expression") String expression,
                                     @Param("parameters") Map<String, Object> parameters,
                                     @Param("query") ApplicationPageQuery query);

    @SelectProvider(type = ApplicationQuerySqlProvider.class, method = "buildSelectAccessibleApplications")
    List<ApplicationQueryRow> selectAccessibleApplications(@Param("expression") String expression,
                                                           @Param("parameters") Map<String, Object> parameters,
                                                           @Param("query") ApplicationPageQuery query,
                                                           @Param("offset") long offset,
                                                           @Param("limit") long limit);
}
```

注意这层只关心：

- 授权表达式是什么
- 授权参数是什么
- 业务查询对象是什么

不要把“范围规则怎么解释”塞进 Mapper。

### 4.10 SQL Provider 怎么保证业务过滤不越权

这是本项目一个必须记住的原则：业务过滤条件和授权范围条件必须做 `AND`，不能做 `OR`。

真实代码如下：

```java
public class ApplicationQuerySqlProvider {

    public String buildCountAccessibleApplications(Map<String, Object> params) {
        return buildSql("SELECT COUNT(1) FROM application_record", params, false);
    }

    public String buildSelectAccessibleApplications(Map<String, Object> params) {
        return buildSql(
                "SELECT application_id AS applicationId, " +
                        "applicant_user_id AS applicantUserId, " +
                        "org_unit_id AS orgUnitId, " +
                        "org_path AS orgPath, " +
                        "category_code AS categoryCode, " +
                        "item_code AS itemCode " +
                        "FROM application_record",
                params,
                true
        );
    }

    private String buildSql(String selectFromSql, Map<String, Object> params, boolean paged) {
        String expression = params == null ? "" : (String) params.get("expression");
        ApplicationPageQuery query = params == null ? null : (ApplicationPageQuery) params.get("query");
        List<String> conditions = new ArrayList<>();
        if (expression != null && !expression.isBlank()) {
            conditions.add("(" + expression + ")");
        }
        appendQueryFilters(conditions, query);

        StringBuilder sql = new StringBuilder(selectFromSql);
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        if (paged) {
            sql.append(" ORDER BY application_id ASC");
            sql.append(" LIMIT #{limit} OFFSET #{offset}");
        }
        return sql.toString();
    }
}
```

一句话解释：

- 范围条件决定“最多能看到什么”。
- 业务过滤条件只能继续缩小范围，绝不能扩大范围。

## 5. 示例 B：`POST /api/files/upload`

这一条主线最适合讲：

1. `multipart/form-data` 怎么进系统。
2. application service 如何编排上传和资产登记。
3. Nacos typed config 如何读取。
4. OSS 上传、DB 写入、日志埋点如何协作。

### 5.1 HTTP 入口

请求示例：

```bash
curl -X POST 'http://localhost:8080/api/files/upload' \
  -H 'Authorization: Bearer <access-token>' \
  -F 'file=@award.pdf' \
  -F 'bizType=application_attachment'
```

典型响应：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {
    "fileId": "file_2f0ab8f0d3814f79a0f6f79907f498d2",
    "bucket": "whut-eval-dev",
    "objectKey": "uploads/application_attachment/20260519/uuid-award.pdf",
    "publicUrl": "https://cdn.example.com/uploads/application_attachment/20260519/uuid-award.pdf",
    "originalFilename": "award.pdf",
    "contentType": "application/pdf",
    "size": 102400
  }
}
```

### 5.2 Controller 怎么写

真实代码如下：

```java
@RestController
@Validated
@RequestMapping("/api/files")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    private final FileUploadApplicationService fileUploadApplicationService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StoredFileDescriptorView> upload(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(value = "bizType", required = false) String bizType)
            throws IOException {
        AppLog.info(log, "file.upload.request.received",
                "bizType", bizType,
                "originalFilename", file.getOriginalFilename(),
                "contentType", file.getContentType(),
                "size", file.getSize());
        if (file.isEmpty()) {
            AppLog.warn(log, "file.upload.request.rejected",
                    "reason", "empty-file",
                    "bizType", bizType,
                    "originalFilename", file.getOriginalFilename(),
                    "contentType", file.getContentType(),
                    "size", file.getSize());
            throw new ValidationException("上传文件不能为空");
        }
        try {
            StoredFileDescriptor descriptor = fileUploadApplicationService.upload(new UploadFileCommand(
                    file.getInputStream(),
                    file.getSize(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    bizType
            ));
            AppLog.info(log, "file.upload.request.completed",
                    "bizType", bizType,
                    "fileId", descriptor.getFileId(),
                    "originalFilename", descriptor.getOriginalFilename(),
                    "contentType", descriptor.getContentType(),
                    "size", descriptor.getSize(),
                    "bucket", descriptor.getBucket(),
                    "objectKey", descriptor.getObjectKey());
            return ApiResponse.success(toView(descriptor));
        } catch (IOException exception) {
            AppLog.error(log, exception, "file.upload.request.io-failed",
                    "bizType", bizType,
                    "originalFilename", file.getOriginalFilename(),
                    "contentType", file.getContentType(),
                    "size", file.getSize());
            throw exception;
        }
    }
}
```

这段代码最值得抄的是两个点：

- HTTP 层日志就在入口打，不要等到深层报错才回头猜发生了什么。
- `MultipartFile` 不要一路往下传，立刻转换成 application 层自己的命令对象。

### 5.3 中间类怎么定义

#### 5.3.1 上传命令

```java
public class UploadFileCommand {

    private final InputStream inputStream;
    private final long size;
    private final String originalFilename;
    private final String contentType;
    private final String bizType;

    public UploadFileCommand(InputStream inputStream,
                             long size,
                             String originalFilename,
                             String contentType,
                             String bizType) {
        this.inputStream = inputStream;
        this.size = size;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.bizType = bizType;
    }
}
```

#### 5.3.2 application 层标准化返回对象

```java
public class StoredFileDescriptor {

    private final String fileId;
    private final String bucket;
    private final String objectKey;
    private final String publicUrl;
    private final String originalFilename;
    private final String contentType;
    private final long size;

    public StoredFileDescriptor(String fileId,
                                String bucket,
                                String objectKey,
                                String publicUrl,
                                String originalFilename,
                                String contentType,
                                long size) {
        this.fileId = fileId;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.publicUrl = publicUrl;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.size = size;
    }
}
```

#### 5.3.3 接口层返回视图

```java
public class StoredFileDescriptorView {

    private final String fileId;
    private final String bucket;
    private final String objectKey;
    private final String publicUrl;
    private final String originalFilename;
    private final String contentType;
    private final long size;

    public StoredFileDescriptorView(String fileId,
                                    String bucket,
                                    String objectKey,
                                    String publicUrl,
                                    String originalFilename,
                                    String contentType,
                                    long size) {
        this.fileId = fileId;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.publicUrl = publicUrl;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.size = size;
    }
}
```

### 5.4 Application Service 怎么编排

真实代码如下：

```java
@Service
public class FileUploadApplicationService {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final FileStorageService fileStorageService;
    private final FileAssetRegistry fileAssetRegistry;

    public StoredFileDescriptor upload(UploadFileCommand command) {
        UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
        StoredFileDescriptor storedFileDescriptor = fileStorageService.store(command);
        return fileAssetRegistry.registerUploadedFile(
                storedFileDescriptor,
                authorizationContext.getUserId(),
                authorizationContext.getIdentity()
        );
    }
}
```

这段的意思很清楚：

- 先确认当前请求有登录用户。
- 先传到对象存储。
- 再把文件元数据登记到 `file_asset`。
- 上传人身份来自当前上下文，而不是前端传参。

### 5.5 读配置：Nacos typed config 怎么进来

#### 5.5.1 `application.yml` 注册配置定义

```yaml
infra:
  nacos:
    definitions:
      - name: oss-storage-config
        data-id: whut-eval-oss-storage.yaml
        group: WHUT_EVAL
        timeout-ms: 3000
        format: YAML
        required: true
        auto-refresh: true
```

#### 5.5.2 启动时预加载并 materialize

```java
public class ConfigBootstrapInitializer implements InitializingBean {

    @Override
    public void afterPropertiesSet() {
        List<ConfigDefinition> definitions = definitionRegistry.getDefinitions();
        if (definitions.isEmpty()) {
            throw new NacosBootstrapException("No ConfigDefinition registered for nacos bootstrap", null);
        }

        try {
            for (ConfigDefinition definition : definitions) {
                RawConfigPayload payload = loadAtStartup(definition);
                if (payload != null) {
                    snapshotRepository.save(definition.name(), payload);
                    typedConfigMaterializer.materialize(definition.name(), payload);
                }
                if (definition.autoRefresh()) {
                    configSubscriber.subscribe(definition, (changedDefinition, changedPayload) -> {
                        snapshotRepository.save(changedDefinition.name(), changedPayload);
                        typedConfigMaterializer.materialize(changedDefinition.name(), changedPayload);
                        AppLog.info(log, "nacos.config.refreshed",
                                "definition", changedDefinition.name(),
                                "dataId", changedDefinition.resource().dataId(),
                                "group", changedDefinition.resource().group());
                    });
                }
            }
            AppLog.info(log, "nacos.bootstrap.completed", "definitionCount", definitions.size());
        } catch (NacosBootstrapException exception) {
            AppLog.error(log, exception, "nacos.bootstrap.failed");
            throw exception;
        }
    }
}
```

#### 5.5.3 业务侧不要直接用 definition name 字符串

```java
@Component
public class OssStorageConfigProvider {

    public static final String DEFINITION_NAME = "oss-storage-config";

    private final TypedConfigRepository typedConfigRepository;

    public Optional<OssStorageConfig> currentConfig() {
        return typedConfigRepository.find(DEFINITION_NAME, OssStorageConfig.class);
    }

    public OssStorageConfig requiredConfig() {
        return currentConfig().orElseThrow(() -> new ConfigLoadException(
                "Required typed config not found: " + DEFINITION_NAME
        ));
    }
}
```

### 5.6 对象存储服务怎么写

真实代码如下：

```java
@Service
public class OssFileStorageService implements FileStorageService {

    private final OssStorageConfigProvider ossStorageConfigProvider;
    private final OssObjectStorageClient ossObjectStorageClient;

    @Override
    public StoredFileDescriptor store(UploadFileCommand command) {
        AppLog.info(log, "file.upload.storage.started",
                "bizType", command.getBizType(),
                "originalFilename", command.getOriginalFilename(),
                "contentType", command.getContentType(),
                "size", command.getSize());
        validateCommand(command);
        OssStorageConfig config;
        try {
            config = ossStorageConfigProvider.requiredConfig();
        } catch (ConfigLoadException exception) {
            AppLog.error(log, exception, "file.upload.storage.config-missing",
                    "bizType", command.getBizType(),
                    "originalFilename", command.getOriginalFilename(),
                    "size", command.getSize());
            throw exception;
        }
        if (!config.isEnabled()) {
            AppLog.warn(log, "file.upload.storage.disabled",
                    "bizType", command.getBizType(),
                    "originalFilename", command.getOriginalFilename(),
                    "bucket", config.getBucket());
            throw new ValidationException("OSS 文件存储当前未启用");
        }
        String objectKey = buildObjectKey(config, command);
        StoredOssObject storedObject = ossObjectStorageClient.putObject(
                config,
                objectKey,
                command.getInputStream(),
                command.getSize(),
                command.getContentType()
        );
        StoredFileDescriptor descriptor = new StoredFileDescriptor(
                storedObject.getBucket(),
                storedObject.getObjectKey(),
                storedObject.getPublicUrl(),
                command.getOriginalFilename(),
                command.getContentType(),
                command.getSize()
        );
        AppLog.info(log, "file.upload.storage.completed",
                "bizType", command.getBizType(),
                "bucket", descriptor.getBucket(),
                "objectKey", descriptor.getObjectKey(),
                "contentType", descriptor.getContentType(),
                "size", descriptor.getSize());
        return descriptor;
    }
}
```

这段代码同时示范了：

- 读配置
- 参数校验
- 打日志
- 调用外部依赖
- 返回标准化对象

### 5.7 OSS SDK 接入点怎么写

真实代码如下：

```java
@Component
public class DefaultOssObjectStorageClient implements OssObjectStorageClient {

    @Override
    public StoredOssObject putObject(OssStorageConfig config,
                                     String objectKey,
                                     InputStream inputStream,
                                     long size,
                                     String contentType) {
        OSS ossClient = null;
        try {
            ossClient = new OSSClientBuilder().build(
                    config.getEndpoint(),
                    config.getAccessKeyId(),
                    config.getAccessKeySecret()
            );
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            if (contentType != null && !contentType.isBlank()) {
                metadata.setContentType(contentType);
            }
            ossClient.putObject(config.getBucket(), objectKey, inputStream, metadata);
            return new StoredOssObject(
                    config.getBucket(),
                    objectKey,
                    buildPublicUrl(config, objectKey)
            );
        } catch (Exception exception) {
            AppLog.error(log, exception, "file.upload.oss.put-object.failed",
                    "endpoint", config.getEndpoint(),
                    "bucket", config.getBucket(),
                    "objectKey", objectKey,
                    "contentType", contentType,
                    "size", size);
            throw new FileStorageException("Failed to upload file to OSS", exception);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }
}
```

### 5.8 写 DB：`file_asset` 怎么登记

#### 5.8.1 先定义 application 层登记接口

```java
public interface FileAssetRegistry {

    StoredFileDescriptor registerUploadedFile(StoredFileDescriptor descriptor,
                                             Long uploaderUserId,
                                             String uploaderIdentity);
}
```

#### 5.8.2 MyBatis 仓储实现

```java
@Service
public class MybatisFileAssetRegistry implements FileAssetRegistry {

    private static final String FILE_STATUS_ACTIVE = "ACTIVE";
    private static final String UPLOAD_CHANNEL_SELF = "SELF_UPLOAD";

    private final FileAssetWriteMapper fileAssetWriteMapper;

    @Override
    public StoredFileDescriptor registerUploadedFile(StoredFileDescriptor descriptor,
                                                     Long uploaderUserId,
                                                     String uploaderIdentity) {
        String fileId = generateFileId();
        LocalDateTime now = LocalDateTime.now();
        FileAssetDO fileAssetDO = new FileAssetDO();
        fileAssetDO.setFileId(fileId);
        fileAssetDO.setStorageKey(descriptor.getObjectKey());
        fileAssetDO.setBucket(descriptor.getBucket());
        fileAssetDO.setOriginalFilename(descriptor.getOriginalFilename());
        fileAssetDO.setContentType(descriptor.getContentType());
        fileAssetDO.setSize(descriptor.getSize());
        fileAssetDO.setUploaderUserId(uploaderUserId);
        fileAssetDO.setUploaderType(resolveUploaderType(uploaderIdentity));
        fileAssetDO.setUploadChannel(UPLOAD_CHANNEL_SELF);
        fileAssetDO.setStatus(FILE_STATUS_ACTIVE);
        fileAssetDO.setCreatedAt(now);
        fileAssetDO.setUpdatedAt(now);
        try {
            fileAssetWriteMapper.insert(fileAssetDO);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("上传文件登记冲突，请重试");
        }
        return new StoredFileDescriptor(
                fileId,
                descriptor.getBucket(),
                descriptor.getObjectKey(),
                descriptor.getPublicUrl(),
                descriptor.getOriginalFilename(),
                descriptor.getContentType(),
                descriptor.getSize()
        );
    }
}
```

#### 5.8.3 Mapper 写 SQL

```java
@Mapper
public interface FileAssetWriteMapper {

    @Insert("INSERT INTO file_asset (file_id, storage_key, bucket, original_filename, content_type, size, sha256, uploader_user_id, uploader_type, upload_channel, status, created_at, updated_at) " +
            "VALUES (#{fileId}, #{storageKey}, #{bucket}, #{originalFilename}, #{contentType}, #{size}, #{sha256}, #{uploaderUserId}, #{uploaderType}, #{uploadChannel}, #{status}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FileAssetDO fileAssetDO);
}
```

这一条链路已经完整覆盖了：

- 读当前用户
- 读 Nacos 配置
- 写 OSS
- 写 DB
- 记录日志

## 6. 示例 C：`POST /api/student/preferences`

这一条主线最适合讲：

1. `@RequestBody` 请求体验证怎么做。
2. 写接口怎么放事务。
3. 写 DB 后怎么做缓存失效。
4. 冲突异常如何映射成 `409`。

### 6.1 HTTP 入口

请求示例：

```bash
curl -X POST 'http://localhost:8080/api/student/preferences' \
  -H 'Authorization: Bearer <access-token>' \
  -H 'Content-Type: application/json' \
  -d '{
    "preferredTheme": "dark",
    "notificationsEnabled": true
  }'
```

典型响应：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {
    "id": 1,
    "userId": 2001,
    "preferredTheme": "dark",
    "notificationsEnabled": true
  }
}
```

### 6.2 Controller 怎么写

真实代码如下：

```java
@RestController
@Validated
@RequestMapping("/api/student/preferences")
public class StudentPreferenceController {

    private final UserPreferenceCommandApplicationService userPreferenceCommandApplicationService;

    public StudentPreferenceController(UserPreferenceCommandApplicationService userPreferenceCommandApplicationService) {
        this.userPreferenceCommandApplicationService = userPreferenceCommandApplicationService;
    }

    @PostMapping
    public ApiResponse<UserPreferenceView> createPreference(@Valid @RequestBody CreateUserPreferenceRequest request) {
        UserPreferenceView view = userPreferenceCommandApplicationService.createCurrentUserPreference(
                new CreateUserPreferenceCommand(request.getPreferredTheme(), request.getNotificationsEnabled())
        );
        return ApiResponse.success(view);
    }
}
```

注意这里的两个注解：

- `@RequestBody` 负责接 JSON。
- `@Valid` 负责触发参数校验。

### 6.3 入参、命令、出参、聚合怎么定义

#### 6.3.1 Request

```java
public class CreateUserPreferenceRequest {

    @NotBlank(message = "preferredTheme 不能为空")
    private String preferredTheme;

    @NotNull(message = "notificationsEnabled 不能为空")
    private Boolean notificationsEnabled;

    public String getPreferredTheme() {
        return preferredTheme;
    }

    public void setPreferredTheme(String preferredTheme) {
        this.preferredTheme = preferredTheme;
    }

    public Boolean getNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(Boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }
}
```

#### 6.3.2 Command

```java
public class CreateUserPreferenceCommand {

    private final String preferredTheme;
    private final Boolean notificationsEnabled;

    public CreateUserPreferenceCommand(String preferredTheme, Boolean notificationsEnabled) {
        this.preferredTheme = preferredTheme;
        this.notificationsEnabled = notificationsEnabled;
    }
}
```

#### 6.3.3 View

```java
public class UserPreferenceView {

    private final Long id;
    private final Long userId;
    private final String preferredTheme;
    private final Boolean notificationsEnabled;

    public UserPreferenceView(Long id, Long userId, String preferredTheme, Boolean notificationsEnabled) {
        this.id = id;
        this.userId = userId;
        this.preferredTheme = preferredTheme;
        this.notificationsEnabled = notificationsEnabled;
    }
}
```

#### 6.3.4 Domain Aggregate

```java
public record UserPreference(Long id,
                             Long userId,
                             String preferredTheme,
                             Boolean notificationsEnabled) {
}
```

### 6.4 Application Service 怎么写

真实代码如下：

```java
@Service
public class UserPreferenceCommandApplicationService {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserPreferenceCacheGateway userPreferenceCacheGateway;

    @Transactional
    public UserPreferenceView createCurrentUserPreference(CreateUserPreferenceCommand command) {
        UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
        Long userId = authorizationContext.getUserId();
        if (userPreferenceRepository.existsByUserId(userId)) {
            throw new ConflictException("当前用户已存在偏好设置，请改用更新接口");
        }

        UserPreference savedPreference = userPreferenceRepository.save(new UserPreference(
                null,
                userId,
                command.getPreferredTheme(),
                command.getNotificationsEnabled()
        ));
        userPreferenceCacheGateway.evictByUserId(userId);
        return new UserPreferenceView(
                savedPreference.id(),
                savedPreference.userId(),
                savedPreference.preferredTheme(),
                savedPreference.notificationsEnabled()
        );
    }
}
```

这段代码同时演示了：

- 从上下文拿当前用户
- 写前做冲突判断
- `@Transactional` 放在 application service
- 写成功后做缓存失效

### 6.5 Repository 和 Mapper 怎么写

#### 6.5.1 领域仓储接口

```java
public interface UserPreferenceRepository {

    boolean existsByUserId(Long userId);

    Optional<UserPreference> findByUserId(Long userId);

    UserPreference save(UserPreference userPreference);
}
```

#### 6.5.2 Infra Repository

```java
@Repository
public class MybatisPlusUserPreferenceRepository implements UserPreferenceRepository {

    private final UserPreferenceMapper userPreferenceMapper;

    @Override
    public boolean existsByUserId(Long userId) {
        return userPreferenceMapper.existsByUserId(userId);
    }

    @Override
    public Optional<UserPreference> findByUserId(Long userId) {
        return Optional.ofNullable(userPreferenceMapper.selectByUserId(userId))
                .map(this::toDomain);
    }

    @Override
    public UserPreference save(UserPreference userPreference) {
        UserPreferenceDO userPreferenceDO = new UserPreferenceDO();
        userPreferenceDO.setUserId(userPreference.userId());
        userPreferenceDO.setPreferredTheme(userPreference.preferredTheme());
        userPreferenceDO.setNotificationsEnabled(userPreference.notificationsEnabled());
        userPreferenceMapper.insert(userPreferenceDO);
        return toDomain(userPreferenceDO);
    }
}
```

#### 6.5.3 Mapper

```java
@Mapper
public interface UserPreferenceMapper {

    @Select("SELECT COUNT(1) > 0 FROM user_preference WHERE user_id = #{userId}")
    boolean existsByUserId(@Param("userId") Long userId);

    @Select("SELECT id, user_id, preferred_theme, notifications_enabled FROM user_preference WHERE user_id = #{userId}")
    UserPreferenceDO selectByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO user_preference (user_id, preferred_theme, notifications_enabled) VALUES (#{userId}, #{preferredTheme}, #{notificationsEnabled})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserPreferenceDO userPreferenceDO);
}
```

### 6.6 缓存失效调用点怎么固定

当前项目里，用户偏好缓存还没有真正接 Redis，但调用点已经固定好了。

#### 6.6.1 application 层缓存网关

```java
public interface UserPreferenceCacheGateway {

    void evictByUserId(Long userId);
}
```

#### 6.6.2 当前空实现

```java
@Component
public class NoopUserPreferenceCacheGateway implements UserPreferenceCacheGateway {

    @Override
    public void evictByUserId(Long userId) {
        // 预留给后续接入 Redis 或本地缓存时实现。
    }
}
```

这说明一个很重要的工程习惯：

- 即使缓存还没上，也先把“写后失效”的调用位置固定下来。
- 以后把空实现换成 Redis 实现，不需要改业务编排。

## 7. 补充切面：真实 Redis 读写样例

如果你要看真正已经接了 Redis 的代码，不要去偏好设置链路，看 IAM 用户查询链路。

### 7.1 Redis 网关怎么写

```java
@Component
public class RedisUserCacheGateway implements UserCacheGateway {

    private static final Duration USER_CACHE_TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Optional<IamUser> getByUserNo(String userNo) {
        Object value = redisTemplate.opsForValue().get(CacheKeyBuilder.iamUserByUserNo(userNo));
        if (value instanceof IamUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    @Override
    public void put(IamUser user) {
        redisTemplate.opsForValue().set(CacheKeyBuilder.iamUserByUserNo(user.userNo()), user, USER_CACHE_TTL);
    }

    @Override
    public void evictByUserNo(String userNo) {
        redisTemplate.delete(CacheKeyBuilder.iamUserByUserNo(userNo));
    }
}
```

缓存 key 也统一由工具类生成：

```java
public final class CacheKeyBuilder {

    public static String iamUserByUserNo(String userNo) {
        return "iam:user:userNo:" + userNo;
    }
}
```

### 7.2 Cache-Aside 仓储怎么写

真实代码如下：

```java
@Repository
public class MybatisPlusIamUserQueryRepository implements IamUserQueryRepository {

    private final IamUserMapper iamUserMapper;
    private final UserCacheGateway userCacheGateway;

    @Override
    public Optional<IamUser> findByUserNo(String userNo) {
        Optional<IamUser> cached = userCacheGateway.getByUserNo(userNo);
        if (cached.isPresent()) {
            return cached;
        }
        LambdaQueryWrapper<IamUserDO> wrapper = new LambdaQueryWrapper<IamUserDO>()
                .eq(IamUserDO::getUserNo, userNo)
                .last("limit 1");
        IamUserDO user = iamUserMapper.selectOne(wrapper);
        if (user == null) {
            return Optional.empty();
        }
        IamUser domain = toDomain(user);
        userCacheGateway.put(domain);
        return Optional.of(domain);
    }
}
```

这是标准的 cache-aside：

- 先读 Redis
- Redis 没命中再读 DB
- 读到 DB 后回填 Redis

如果以后做“修改用户信息”接口，对应动作就应该是：

```java
userRepository.update(...);
userCacheGateway.evictByUserNo(userNo);
```

也就是：

- 先写 DB
- 再删缓存

而不是先改缓存再赌 DB 一定成功。

## 8. 日志应该怎么打

项目里统一用 `AppLog` 打结构化日志，真实代码如下：

```java
public final class AppLog {

    public static void info(Logger logger, String event, Object... keyValues) {
        if (logger.isInfoEnabled()) {
            logger.info(buildMessage(event, keyValues));
        }
    }

    public static void warn(Logger logger, String event, Object... keyValues) {
        if (logger.isWarnEnabled()) {
            logger.warn(buildMessage(event, keyValues));
        }
    }

    public static void error(Logger logger, Throwable throwable, String event, Object... keyValues) {
        logger.error(buildMessage(event, keyValues), throwable);
    }
}
```

日志口径建议直接抄现有风格：

- 请求入口：`file.upload.request.received`
- 请求完成：`file.upload.request.completed`
- 参数拒绝：`file.upload.request.rejected`
- 配置缺失：`file.upload.storage.config-missing`
- 外部依赖失败：`file.upload.oss.put-object.failed`

规则只有一个：事件名稳定，字段名稳定，不要打一堆看起来像自然语言的日志。

## 9. 异常和错误码怎么返回

### 9.1 统一错误码

```java
public enum CommonErrorCode implements ErrorCode {
    VALIDATION_ERROR("VAL-4001", 400, "请求参数不合法"),
    AUTHENTICATION_FAILED("AUTH-4010", 401, "认证失败"),
    TOKEN_EXPIRED("AUTH-4011", 401, "令牌已过期"),
    TOKEN_INVALID("AUTH-4012", 401, "令牌非法"),
    ACCESS_DENIED("AUTH-4030", 403, "无权限访问"),
    RESOURCE_NOT_FOUND("RES-4040", 404, "资源不存在"),
    RESOURCE_CONFLICT("BIZ-4090", 409, "资源状态冲突"),
    BIZ_RULE_VIOLATION("BIZ-4091", 409, "业务规则不满足")
}
```

### 9.2 典型业务异常

```java
public class ValidationException extends BaseAppException {

    public ValidationException(String message) {
        super(CommonErrorCode.VALIDATION_ERROR, message);
    }
}
```

```java
public class ConflictException extends BaseAppException {

    public ConflictException(String message) {
        super(CommonErrorCode.RESOURCE_CONFLICT, message);
    }
}
```

```java
public class AccessDeniedAppException extends BaseAppException {

    public AccessDeniedAppException(String message) {
        super(CommonErrorCode.ACCESS_DENIED, message);
    }
}
```

### 9.3 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseAppException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseAppException(BaseAppException exception) {
        return ResponseEntity.status(exception.getErrorCode().httpStatus())
                .body(ApiResponse.failure(exception.getErrorCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(CommonErrorCode.VALIDATION_ERROR.defaultMessage());
        ValidationException validationException = new ValidationException(message);
        return ResponseEntity.status(validationException.getErrorCode().httpStatus())
                .body(ApiResponse.failure(validationException.getErrorCode(), validationException.getMessage()));
    }
}
```

新人只要记住：

- 自定义业务异常尽量继承 `BaseAppException`。
- 统一错误码不要自己手搓 JSON。
- 参数校验失败最后也会落成统一的 `ApiResponse.failure(...)`。

## 10. 新人第一次开发接口的最小 checklist

如果你要在本项目里新增一个接口，按下面顺序做：

1. 先判断这是查询接口、写接口、上传接口，还是缓存接口，不要一开始就把所有职责混在一起。
2. 先定路由前缀，学生端用 `/api/student/...`，管理端用 `/api/admin/...`。
3. 先定权限码，确认它是 `authentication`、`authorization`、`scope` 里的哪一层。
4. Controller 只做注解、参数解析、返回包装，不要直接写仓储逻辑。
5. application service 统一从 `UserAuthorizationContextAssembler` 取当前用户。
6. 需要范围控制的查询，必须走 `AuthorizationScopeEvaluator -> ScopePredicateBuilder -> SqlTranslator -> Mapper` 这条链路。
7. 业务过滤条件和范围条件在 SQL 中必须是 `AND`。
8. 写接口优先在 application service 上放事务边界。
9. 读配置优先走 typed config provider，不要直接散落 definition name。
10. 涉及文件上传时，先传 OSS，再登记 `file_asset`。
11. 读缓存走 cache-aside；写接口优先“写 DB + 删缓存”。
12. 日志统一用 `AppLog`，事件名和字段名保持稳定。
13. 错误统一复用 `CommonErrorCode` 和 `GlobalExceptionHandler`，不要手写不一致的错误壳。

## 11. 最后一句

如果你现在要开始写一个新接口，最推荐的阅读顺序是：

1. 先读本文件第 4 章，理解“权限 + 范围 + 查询”。
2. 再读第 5 章，理解“配置 + OSS + DB + 日志”。
3. 再读第 6 章，理解“请求体验证 + 事务 + 写后缓存失效”。
4. 最后读第 7 章，把 Redis cache-aside 补齐。

读完这四段，再去写代码，基本就不会把层次写乱。
