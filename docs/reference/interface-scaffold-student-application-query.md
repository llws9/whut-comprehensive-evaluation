# 学生查询申请接口脚手架

## 1. 适用场景

这份脚手架针对真实业务场景：

- 接口：`GET /api/student/query/applications`
- 目标：查询当前学生在 `application.view.self` 权限下可见的申请列表
- 典型能力：分页查询、当前用户上下文、权限校验、范围控制、Mapper 查询、统一返回

这不是抽象模板，而是把刚才的 `Xxx` 全部替换成当前项目真实场景后的“可直接复制骨架”。

如果你要新做一个“同类查询接口”，最稳妥的方式是：

1. 先照抄这一套分层。
2. 再替换路由、权限码、Query 字段、View 字段、Mapper SQL。
3. 不要跳过 `UserAuthorizationContextAssembler`、`ApplicationAccessContext`、`ApplicationScopeSqlTranslator` 这一整条链路。

## 2. 你最终会落哪些文件

如果你做一个和“学生查询申请”同构的新接口，目录结构建议照着下面摆：

```text
whut-eval-interfaces/
  src/main/java/edu/whut/eval/interfaces/student/
    StudentQueryController.java

whut-eval-application/
  src/main/java/edu/whut/eval/application/application/service/
    ApplicationQueryApplicationService.java
  src/main/java/edu/whut/eval/application/application/query/
    ApplicationRecordView.java

whut-eval-domain/
  src/main/java/edu/whut/eval/domain/application/query/
    ApplicationPageQuery.java
    ApplicationAccessContext.java
  src/main/java/edu/whut/eval/domain/application/model/
    ApplicationRecord.java
  src/main/java/edu/whut/eval/domain/application/repository/
    ApplicationQueryRepository.java

whut-eval-infra/
  src/main/java/edu/whut/eval/infra/persistence/repository/
    MybatisPlusApplicationQueryRepository.java
  src/main/java/edu/whut/eval/infra/persistence/mapper/
    ApplicationQueryMapper.java
    ApplicationQuerySqlProvider.java
  src/main/java/edu/whut/eval/infra/persistence/query/
    ApplicationQueryRow.java
```

## 3. 先看一遍完整调用链

```text
GET /api/student/query/applications
  -> StudentQueryController
  -> ApplicationQueryApplicationService
  -> ApplicationQueryRepository
  -> MybatisPlusApplicationQueryRepository
  -> ApplicationQueryMapper
  -> ApplicationQuerySqlProvider
  -> application_record
```

权限与范围的实际链路是：

```text
JWT
  -> SecurityContext
  -> UserAuthorizationContextAssembler
  -> AuthorizationScopeEvaluator
  -> ScopePredicateBuilder
  -> ApplicationScopeSqlTranslator
  -> SQL WHERE expression
```

## 4. HTTP 入口脚手架

### 4.1 请求示例

```bash
curl -X GET 'http://localhost:8080/api/student/query/applications?pageNo=1&pageSize=20&categoryCode=MORAL' \
  -H 'Authorization: Bearer <access-token>'
```

### 4.2 响应示例

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

## 5. Controller 脚手架

文件建议：

- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/StudentQueryController.java`

可直接复制的骨架如下：

```java
package edu.whut.eval.interfaces.student;

import edu.whut.eval.application.application.query.ApplicationRecordView;
import edu.whut.eval.application.application.service.ApplicationQueryApplicationService;
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.domain.application.query.ApplicationPageQuery;
import edu.whut.eval.domain.shared.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/query")
@Validated
public class StudentQueryController {

    private final ApplicationQueryApplicationService applicationQueryApplicationService;

    public StudentQueryController(ApplicationQueryApplicationService applicationQueryApplicationService) {
        this.applicationQueryApplicationService = applicationQueryApplicationService;
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

这一层只做四件事：

1. 定义路由
2. 绑定权限注解
3. 解析 HTTP 参数
4. 包装统一返回

不要在这里做：

- 直接读 `SecurityContextHolder`
- 直接调 Mapper
- 直接拼 SQL

## 6. Query 与 View 脚手架

### 6.1 查询对象

文件建议：

- `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/query/ApplicationPageQuery.java`

```java
package edu.whut.eval.domain.application.query;

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

    public long getPageNo() {
        return pageNo;
    }

    public long getPageSize() {
        return pageSize;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getApplicantUserId() {
        return applicantUserId;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getItemCode() {
        return itemCode;
    }

    public long getOffset() {
        return (pageNo - 1) * pageSize;
    }
}
```

### 6.2 返回视图

文件建议：

- `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ApplicationRecordView.java`

```java
package edu.whut.eval.application.application.query;

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

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getApplicantUserId() {
        return applicantUserId;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public String getOrgPath() {
        return orgPath;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getItemCode() {
        return itemCode;
    }
}
```

## 7. Application Service 脚手架

文件建议：

- `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ApplicationQueryApplicationService.java`

```java
package edu.whut.eval.application.application.service;

import edu.whut.eval.application.application.query.ApplicationRecordView;
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.domain.application.model.ApplicationRecord;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.query.ApplicationPageQuery;
import edu.whut.eval.domain.application.repository.ApplicationQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import org.springframework.stereotype.Service;

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

    private ApplicationRecordView toView(ApplicationRecord record) {
        return new ApplicationRecordView(
                record.applicationId(),
                record.applicantUserId(),
                record.orgUnitId(),
                record.orgPath(),
                record.categoryCode(),
                record.itemCode()
        );
    }
}
```

这里的关键职责只有三件：

1. 从 `UserAuthorizationContextAssembler` 取当前用户
2. 显式做权限兜底
3. 把运行时上下文转换为领域访问上下文

## 8. AccessContext 与 Repository Interface 脚手架

### 8.1 访问上下文

文件建议：

- `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/query/ApplicationAccessContext.java`

```java
package edu.whut.eval.domain.application.query;

import edu.whut.eval.domain.iam.model.IamScopeRule;

import java.util.List;
import java.util.Set;

public class ApplicationAccessContext {

    private final Long userId;
    private final String userNo;
    private final String userName;
    private final String identity;
    private final Set<String> roles;
    private final Set<String> authorities;
    private final List<IamScopeRule> scopeRules;
    private final String permissionCode;

    public ApplicationAccessContext(Long userId,
                                    String userNo,
                                    String userName,
                                    String identity,
                                    Set<String> roles,
                                    Set<String> authorities,
                                    List<IamScopeRule> scopeRules,
                                    String permissionCode) {
        this.userId = userId;
        this.userNo = userNo;
        this.userName = userName;
        this.identity = identity;
        this.roles = roles == null ? Set.of() : Set.copyOf(roles);
        this.authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
        this.scopeRules = scopeRules == null ? List.of() : List.copyOf(scopeRules);
        this.permissionCode = permissionCode;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserNo() {
        return userNo;
    }

    public String getUserName() {
        return userName;
    }

    public String getIdentity() {
        return identity;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getAuthorities() {
        return authorities;
    }

    public List<IamScopeRule> getScopeRules() {
        return scopeRules;
    }

    public String getPermissionCode() {
        return permissionCode;
    }
}
```

### 8.2 Repository 接口

文件建议：

- `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/repository/ApplicationQueryRepository.java`

```java
package edu.whut.eval.domain.application.repository;

import edu.whut.eval.domain.application.model.ApplicationRecord;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.query.ApplicationPageQuery;
import edu.whut.eval.domain.shared.PageResult;

public interface ApplicationQueryRepository {

    PageResult<ApplicationRecord> pageAccessibleApplications(ApplicationAccessContext accessContext,
                                                             ApplicationPageQuery query);
}
```

## 9. Infra Repository 脚手架

文件建议：

- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusApplicationQueryRepository.java`

```java
package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.application.auth.model.ApplicationScopePredicate;
import edu.whut.eval.application.auth.model.AuthorizationScopeSet;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.AuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.ScopePredicateBuilder;
import edu.whut.eval.domain.application.model.ApplicationRecord;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.query.ApplicationPageQuery;
import edu.whut.eval.domain.application.repository.ApplicationQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.persistence.mapper.ApplicationQueryMapper;
import edu.whut.eval.infra.persistence.query.ApplicationQueryRow;
import edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator;
import edu.whut.eval.infra.security.sql.SqlPredicateFragment;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MybatisPlusApplicationQueryRepository implements ApplicationQueryRepository {

    private final AuthorizationScopeEvaluator authorizationScopeEvaluator;
    private final ScopePredicateBuilder scopePredicateBuilder;
    private final ApplicationScopeSqlTranslator applicationScopeSqlTranslator;
    private final ApplicationQueryMapper applicationQueryMapper;

    public MybatisPlusApplicationQueryRepository(AuthorizationScopeEvaluator authorizationScopeEvaluator,
                                                 ScopePredicateBuilder scopePredicateBuilder,
                                                 ApplicationScopeSqlTranslator applicationScopeSqlTranslator,
                                                 ApplicationQueryMapper applicationQueryMapper) {
        this.authorizationScopeEvaluator = authorizationScopeEvaluator;
        this.scopePredicateBuilder = scopePredicateBuilder;
        this.applicationScopeSqlTranslator = applicationScopeSqlTranslator;
        this.applicationQueryMapper = applicationQueryMapper;
    }

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

    private UserAuthorizationContext toAuthorizationContext(ApplicationAccessContext accessContext) {
        return new UserAuthorizationContext(
                accessContext.getUserId(),
                accessContext.getUserNo(),
                accessContext.getUserName(),
                accessContext.getIdentity(),
                accessContext.getRoles(),
                accessContext.getAuthorities(),
                accessContext.getScopeRules()
        );
    }

    private ApplicationRecord toDomain(ApplicationQueryRow row) {
        return new ApplicationRecord(
                row.getApplicationId(),
                row.getApplicantUserId(),
                row.getOrgUnitId(),
                row.getOrgPath(),
                row.getCategoryCode(),
                row.getItemCode()
        );
    }
}
```

这一层的核心闭环一定要保留：

```text
evaluate -> predicate -> translator -> mapper
```

## 10. Mapper 与 SQL Provider 脚手架

### 10.1 Mapper

文件建议：

- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ApplicationQueryMapper.java`

```java
package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.domain.application.query.ApplicationPageQuery;
import edu.whut.eval.infra.persistence.query.ApplicationQueryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.util.Map;

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

### 10.2 SQL Provider

文件建议：

- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ApplicationQuerySqlProvider.java`

```java
package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.domain.application.query.ApplicationPageQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private void appendQueryFilters(List<String> conditions, ApplicationPageQuery query) {
        if (query == null) {
            return;
        }
        if (query.getApplicationId() != null) {
            conditions.add("application_id = #{query.applicationId}");
        }
        if (query.getApplicantUserId() != null) {
            conditions.add("applicant_user_id = #{query.applicantUserId}");
        }
        if (query.getOrgUnitId() != null) {
            conditions.add("org_unit_id = #{query.orgUnitId}");
        }
        if (query.getCategoryCode() != null && !query.getCategoryCode().isBlank()) {
            conditions.add("category_code = #{query.categoryCode}");
        }
        if (query.getItemCode() != null && !query.getItemCode().isBlank()) {
            conditions.add("item_code = #{query.itemCode}");
        }
    }
}
```

最重要的一句：

- 业务过滤条件和范围条件必须是 `AND`

## 11. 范围到 SQL 的关键拼装点

如果你复制这套查询链路，但范围不生效，通常不是 Mapper 的锅，而是前面的谓词没有进来。

这里给出两个关键真实拼装点。

### 11.1 `ScopePredicateBuilder`

```java
ApplicationScopePredicate predicate = scopePredicateBuilder.buildForApplication(authorizationContext, scopeSet);
```

### 11.2 `ApplicationScopeSqlTranslator`

```java
SqlPredicateFragment fragment = applicationScopeSqlTranslator.translate(authorizationContext, predicate);
```

真实实现中的重要语义：

- clause 内部字段使用 `AND`
- 多个 clause 之间使用 `OR`
- 最终进入业务 SQL 时，整个范围表达式再与业务过滤条件做 `AND`

也就是：

```text
(范围子句1 OR 范围子句2 OR ...)
AND
(业务筛选条件1 AND 业务筛选条件2 ...)
```

## 12. 统一返回与异常

这条查询链路最终统一返回：

```java
return ApiResponse.success(applicationQueryApplicationService.pageAccessibleApplications(...));
```

如果用户没有该权限，应用服务抛：

```java
throw new AccessDeniedAppException("当前用户无权限访问申请列表");
```

它最终会被 `GlobalExceptionHandler` 统一映射为：

- HTTP 403
- 错误码 `AUTH-4030`

## 13. 新人复制这份脚手架时，只允许改哪些地方

如果你是要新建一个“同构查询接口”，通常只改下面这些位置：

1. `@GetMapping("/applications")` 的路径
2. `@PreAuthorize(...)` 对应的权限码
3. `ApplicationPageQuery` 的查询字段
4. `ApplicationRecordView` 的返回字段
5. `SELECT ... FROM application_record` 对应的表和列
6. `appendQueryFilters(...)` 的业务过滤条件
7. `toView(...)` / `toDomain(...)` 的字段映射

通常不要动这些位置：

1. `requiredAuthorizationContext()`
2. `ensurePermissionGranted(...)`
3. `ApplicationAccessContext`
4. `evaluate -> predicate -> translator -> mapper` 整体顺序
5. `WHERE` 中范围条件与业务条件的 `AND` 关系

## 14. 对照源码

如果你要对照当前仓库中的真实实现，可直接看：

- [StudentQueryController](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/StudentQueryController.java)
- [ApplicationQueryApplicationService](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ApplicationQueryApplicationService.java)
- [ApplicationPageQuery](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-domain/src/main/java/edu/whut/eval/domain/application/query/ApplicationPageQuery.java)
- [ApplicationAccessContext](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-domain/src/main/java/edu/whut/eval/domain/application/query/ApplicationAccessContext.java)
- [ApplicationQueryRepository](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-domain/src/main/java/edu/whut/eval/domain/application/repository/ApplicationQueryRepository.java)
- [MybatisPlusApplicationQueryRepository](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusApplicationQueryRepository.java)
- [ApplicationQueryMapper](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ApplicationQueryMapper.java)
- [ApplicationQuerySqlProvider](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ApplicationQuerySqlProvider.java)
- [ApplicationScopeSqlTranslator](file:///Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/whut-eval-infra/src/main/java/edu/whut/eval/infra/security/sql/ApplicationScopeSqlTranslator.java)

## 15. 一句话记忆

学生查询申请这条链路，新人只要记住这句就够了：

```text
Controller 收参数，Service 拿上下文，Repository 算范围，Mapper 查数据库，SQL 用 AND 收窄结果。
```
