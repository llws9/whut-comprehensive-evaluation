# Minimal C Review Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Minimal C reviewer workflow: list/detail review applications, approve/return/reject submitted applications, append immutable review logs, and enforce A-group `application.review` scope rules.

**Architecture:** Keep state transitions in the `ApplicationSubmission` aggregate. Add review-specific application services, query views, MyBatis mappers, and a `/api/review/applications` controller while reusing existing A-group authorization scope evaluation. Persist review logs in `application_review_log` in the same transaction as application status updates.

**Tech Stack:** Java 17, Spring Boot, Spring MVC, Spring Security method annotations, MyBatis Plus mapper style, H2 MySQL-mode integration tests, JUnit 5, AssertJ, Mockito.

---

## Source Spec

- Primary spec: `docs/superpowers/specs/2026-07-07-minimal-c-review-workflow-design.md`
- Target-state reference: `docs/team-delivery/group-c-review-workflow.md`
- Existing B write/read foundation:
  - `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationSubmission.java`
  - `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ApplicationSubmissionCommandApplicationService.java`
  - `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ApplicationSubmissionDetailApplicationService.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusApplicationSubmissionRepository.java`

## File Structure

### Domain

- Modify `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationSubmission.java`
  - Add review transition methods: `approve`, `returnForFix`, `reject`.
  - Add private `assertReviewable()` that throws `ConflictException`.
- Create `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationReviewAction.java`
  - Command-verb enum for review log actions: `APPROVE`, `RETURN`, `REJECT`.
- Create `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationReviewLog.java`
  - Domain object for immutable review log rows.
- Create `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/repository/ApplicationReviewLogRepository.java`
  - Append and list review logs.
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/repository/ReviewApplicationQueryRepository.java`
  - Review-specific list/detail read repository.
- Create `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/query/ReviewApplicationPageQuery.java`
  - Query object with Minimal C list filters.

### Application Layer

- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/command/ApproveReviewCommand.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/command/ReturnReviewCommand.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/command/RejectReviewCommand.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewActionResultView.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicationListItemView.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicationDetailView.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicationSummaryView.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicantView.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewScoringSnapshotView.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewLogView.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ReviewApplicationAccessValidator.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ReviewApplicationCommandApplicationService.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ReviewApplicationQueryApplicationService.java`

### Infrastructure

- Create `docs/team-delivery/group-c-review-workflow.safe-init.sql`
- Create `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/dataobject/ApplicationReviewLogDO.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicationQueryRow.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicationAttachmentRow.java`
- Create `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ApplicationReviewLogMapper.java`
- Create `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ReviewApplicationQueryMapper.java`
- Create `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ReviewApplicationQuerySqlProvider.java`
- Create `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusApplicationReviewLogRepository.java`
- Create `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusReviewApplicationQueryRepository.java`

### Interfaces

- Create `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/review/ReviewApplicationController.java`
- Create `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/review/request/ApproveReviewRequest.java`
- Create `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/review/request/ReturnReviewRequest.java`
- Create `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/review/request/RejectReviewRequest.java`

### Tests

- Modify `whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java`
- Modify `whut-eval-app/src/test/java/edu/whut/eval/app/application/ApplicationSubmissionStateMachineTest.java`
- Modify `whut-eval-app/src/test/java/edu/whut/eval/app/student/MybatisPlusApplicationSubmissionRepositoryIntegrationTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/review/ApplicationReviewLogRepositoryIntegrationTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationQueryRepositoryIntegrationTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationAccessValidatorTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationCommandApplicationServiceTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationControllerWebMvcTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationControllerSecurityAnnotationTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationSecurityIntegrationTest.java`

---

### Task 1: Non-Destructive C Safe-Init SQL

**Files:**
- Create: `docs/team-delivery/group-c-review-workflow.safe-init.sql`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java`

- [ ] **Step 1: Write failing SQL consistency tests**

Add this constant near the existing `B_GROUP_SAFE_INIT_SQL` and `E_GROUP_SAFE_INIT_SQL` constants:

```java
private static final Path C_GROUP_SAFE_INIT_SQL = TEAM_DELIVERY.resolve("group-c-review-workflow.safe-init.sql");
```

Add these tests before `shouldOnlyReferenceApprovedApplicationsInDGroupApplicationScores`:

```java
@Test
void shouldProvideNonDestructiveCGroupSafeInitSql() throws Exception {
    String sql = Files.readString(C_GROUP_SAFE_INIT_SQL);

    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `application_review_log`");
    assertThat(sql).doesNotContain("DROP TABLE");
    assertThat(sql).doesNotContain("ENGINE=");
    assertThat(sql).doesNotContain("CHARSET");
    assertThat(sql).doesNotContain("COLLATE");
    assertThat(sql).doesNotContain("COMMENT=");
    assertThat(extractCreateTableBlock(sql, "application_review_log"))
            .contains("`id` BIGINT NOT NULL AUTO_INCREMENT")
            .contains("`application_id` BIGINT NOT NULL")
            .contains("`action` VARCHAR(32) NOT NULL")
            .contains("`reviewer_id` BIGINT NOT NULL")
            .contains("`review_role` VARCHAR(64) NOT NULL")
            .contains("`reason` VARCHAR(1000) DEFAULT NULL")
            .contains("`reviewed_at` DATETIME NOT NULL")
            .contains("KEY `idx_application_review_log_application_id` (`application_id`)")
            .contains("KEY `idx_application_review_log_reviewer_id` (`reviewer_id`)");
}

@Test
void shouldRerunCGroupSafeInitSqlWithoutOverwritingRuntimeReviewLogs() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:c_group_safe_init;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")) {
        executeStatements(connection, Files.readString(C_GROUP_SAFE_INIT_SQL));

        connection.createStatement().executeUpdate("""
                INSERT INTO application_review_log (application_id, action, reviewer_id, review_role, reason, reviewed_at)
                VALUES (21013, 'APPROVE', 1010, 'COUNSELOR', 'runtime approve', '2026-07-07 10:00:00')
                """);
        long reviewLogId = singleLong(connection, "SELECT id FROM application_review_log WHERE reason = 'runtime approve'");

        executeStatements(connection, Files.readString(C_GROUP_SAFE_INIT_SQL));

        assertThat(countRows(connection, "application_review_log", "id = " + reviewLogId)).isEqualTo(1);
        assertThat(singleString(connection, "SELECT action FROM application_review_log WHERE id = " + reviewLogId))
                .isEqualTo("APPROVE");
        assertThat(singleString(connection, "SELECT reason FROM application_review_log WHERE id = " + reviewLogId))
                .isEqualTo("runtime approve");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl whut-eval-app -Dtest=TeamDeliverySqlConsistencyTest#shouldProvideNonDestructiveCGroupSafeInitSql,TeamDeliverySqlConsistencyTest#shouldRerunCGroupSafeInitSqlWithoutOverwritingRuntimeReviewLogs -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `docs/team-delivery/group-c-review-workflow.safe-init.sql` does not exist.

- [ ] **Step 3: Add safe-init SQL**

Create `docs/team-delivery/group-c-review-workflow.safe-init.sql` with exactly:

```sql
CREATE TABLE IF NOT EXISTS `application_review_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `action` VARCHAR(32) NOT NULL,
  `reviewer_id` BIGINT NOT NULL,
  `review_role` VARCHAR(64) NOT NULL,
  `reason` VARCHAR(1000) DEFAULT NULL,
  `reviewed_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_application_review_log_application_id` (`application_id`),
  KEY `idx_application_review_log_reviewer_id` (`reviewer_id`)
);
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl whut-eval-app -Dtest=TeamDeliverySqlConsistencyTest#shouldProvideNonDestructiveCGroupSafeInitSql,TeamDeliverySqlConsistencyTest#shouldRerunCGroupSafeInitSqlWithoutOverwritingRuntimeReviewLogs -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add docs/team-delivery/group-c-review-workflow.safe-init.sql whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java
git commit -m "test: add c review log safe init checks"
```

---

### Task 2: Aggregate Review State Transitions

**Files:**
- Modify: `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationSubmission.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/application/ApplicationSubmissionStateMachineTest.java`

- [ ] **Step 1: Write failing state-machine tests**

Add imports:

```java
import edu.whut.eval.common.exception.ConflictException;
import java.time.Instant;
```

Add these tests after `shouldWithdrawSubmittedApplication`:

```java
@Test
void shouldApproveReturnAndRejectSubmittedApplication() {
    ApplicationSubmission submitted = applicationWithStatus(ApplicationSubmissionStatus.SUBMITTED);

    ApplicationSubmission approved = submitted.approve(0L);
    ApplicationSubmission returned = submitted.returnForFix(0L);
    ApplicationSubmission rejected = submitted.reject(0L);

    assertThat(approved.getStatus()).isEqualTo(ApplicationSubmissionStatus.APPROVED);
    assertThat(returned.getStatus()).isEqualTo(ApplicationSubmissionStatus.RETURNED);
    assertThat(rejected.getStatus()).isEqualTo(ApplicationSubmissionStatus.REJECTED);
    assertThat(approved.getVersion()).isEqualTo(1L);
    assertThat(returned.getVersion()).isEqualTo(1L);
    assertThat(rejected.getVersion()).isEqualTo(1L);
}

@Test
void shouldPreserveSubmittedAtAttachmentsAndScoringSnapshotWhenReviewing() {
    ApplicationSubmission submitted = applicationWithStatus(ApplicationSubmissionStatus.SUBMITTED);

    ApplicationSubmission approved = submitted.approve(0L);

    assertThat(approved.getSubmittedAt()).isEqualTo(submitted.getSubmittedAt());
    assertThat(approved.getEvidenceAttachments()).hasSize(1);
    assertThat(approved.getScoringSnapshot()).isNotNull();
    assertThat(approved.getScoringSnapshot().optionCode()).isEqualTo("OPTION_A");
    assertThat(approved.getScoringSnapshot().appliedPoints()).isEqualByComparingTo("2.00");
}

@Test
void shouldRejectReviewActionsFromNonSubmittedStatesWithConflict() {
    for (ApplicationSubmissionStatus status : List.of(
            ApplicationSubmissionStatus.DRAFT,
            ApplicationSubmissionStatus.RETURNED,
            ApplicationSubmissionStatus.APPROVED,
            ApplicationSubmissionStatus.REJECTED,
            ApplicationSubmissionStatus.WITHDRAWN
    )) {
        ApplicationSubmission submission = applicationWithStatus(status);

        assertThatThrownBy(() -> submission.approve(0L))
                .as("approve from " + status)
                .isInstanceOf(ConflictException.class)
                .hasMessage("当前申请状态不允许审核");
        assertThatThrownBy(() -> submission.returnForFix(0L))
                .as("return from " + status)
                .isInstanceOf(ConflictException.class)
                .hasMessage("当前申请状态不允许审核");
        assertThatThrownBy(() -> submission.reject(0L))
                .as("reject from " + status)
                .isInstanceOf(ConflictException.class)
                .hasMessage("当前申请状态不允许审核");
    }
}
```

Change `applicationWithStatus` so it passes a scoring snapshot for submitted/review terminal states:

```java
private ApplicationSubmission applicationWithStatus(ApplicationSubmissionStatus status) {
    return new ApplicationSubmission(
            1L,
            1001L,
            10L,
            "competition",
            "item-1",
            "2025-2026",
            "1",
            "申请标题",
            "申请说明",
            List.of(new AttachmentRef("file-1", "uploads/a.pdf", "a.pdf", "application/pdf", 10L, 1001L)),
            status,
            status == ApplicationSubmissionStatus.DRAFT ? null : Instant.parse("2026-07-06T10:00:00Z"),
            Instant.parse("2026-07-06T09:00:00Z"),
            Instant.parse("2026-07-06T09:00:00Z"),
            0L,
            status == ApplicationSubmissionStatus.DRAFT ? null : submittedSnapshot()
    );
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ApplicationSubmissionStateMachineTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL with missing methods `approve`, `returnForFix`, and `reject`.

- [ ] **Step 3: Implement aggregate transitions**

In `ApplicationSubmission.java`, add these methods after `withdraw`:

```java
/**
 * 审核通过已提交申请。
 */
public ApplicationSubmission approve(long expectedVersion) {
    assertReviewable();
    assertExpectedVersion(expectedVersion);
    return reviewedAs(ApplicationSubmissionStatus.APPROVED);
}

/**
 * 退回已提交申请，允许学生补充后重新提交。
 */
public ApplicationSubmission returnForFix(long expectedVersion) {
    assertReviewable();
    assertExpectedVersion(expectedVersion);
    return reviewedAs(ApplicationSubmissionStatus.RETURNED);
}

/**
 * 拒绝已提交申请，本次申请终态关闭。
 */
public ApplicationSubmission reject(long expectedVersion) {
    assertReviewable();
    assertExpectedVersion(expectedVersion);
    return reviewedAs(ApplicationSubmissionStatus.REJECTED);
}

private ApplicationSubmission reviewedAs(ApplicationSubmissionStatus targetStatus) {
    return new ApplicationSubmission(
            applicationId,
            applicantUserId,
            orgUnitId,
            categoryCode,
            itemCode,
            academicYear,
            term,
            title,
            description,
            evidenceAttachments,
            targetStatus,
            submittedAt,
            createdAt,
            Instant.now(),
            version + 1,
            scoringSnapshot
    );
}
```

Add this guard near `assertWithdrawable`:

```java
private void assertReviewable() {
    if (status != ApplicationSubmissionStatus.SUBMITTED) {
        throw new ConflictException("当前申请状态不允许审核");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ApplicationSubmissionStateMachineTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationSubmission.java whut-eval-app/src/test/java/edu/whut/eval/app/application/ApplicationSubmissionStateMachineTest.java
git commit -m "feat: add application review transitions"
```

---

### Task 3: Review Log Domain and Persistence

**Files:**
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationReviewAction.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationReviewLog.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/repository/ApplicationReviewLogRepository.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/dataobject/ApplicationReviewLogDO.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ApplicationReviewLogMapper.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusApplicationReviewLogRepository.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/review/ApplicationReviewLogRepositoryIntegrationTest.java`

- [ ] **Step 1: Write failing repository integration test**

Create `whut-eval-app/src/test/java/edu/whut/eval/app/review/ApplicationReviewLogRepositoryIntegrationTest.java`:

```java
package edu.whut.eval.app.review;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.domain.application.model.ApplicationReviewAction;
import edu.whut.eval.domain.application.model.ApplicationReviewLog;
import edu.whut.eval.domain.application.repository.ApplicationReviewLogRepository;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.ApplicationReviewLogMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusApplicationReviewLogRepository;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ApplicationReviewLogRepositoryIntegrationTest.TestConfig.class)
class ApplicationReviewLogRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationReviewLogRepository repository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_review_log");
        jdbcTemplate.execute("""
                CREATE TABLE application_review_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    application_id BIGINT NOT NULL,
                    action VARCHAR(32) NOT NULL,
                    reviewer_id BIGINT NOT NULL,
                    review_role VARCHAR(64) NOT NULL,
                    reason VARCHAR(1000) NULL,
                    reviewed_at DATETIME NOT NULL
                )
                """);
    }

    @Test
    void shouldAppendReviewLogAndReturnGeneratedId() {
        ApplicationReviewLog saved = repository.append(new ApplicationReviewLog(
                null,
                21013L,
                ApplicationReviewAction.APPROVE,
                1010L,
                "COUNSELOR",
                "材料完整",
                Instant.parse("2026-07-07T10:00:00Z")
        ));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAction()).isEqualTo(ApplicationReviewAction.APPROVE);
        assertThat(saved.getReviewedAt()).isEqualTo(Instant.parse("2026-07-07T10:00:00Z"));
        assertThat(jdbcTemplate.queryForObject("SELECT action FROM application_review_log WHERE id = ?", String.class, saved.getId()))
                .isEqualTo("APPROVE");
    }

    @Test
    void shouldListLogsByApplicationIdInStableOrder() {
        repository.append(new ApplicationReviewLog(null, 21013L, ApplicationReviewAction.RETURN, 1010L, "COUNSELOR", "补材料", Instant.parse("2026-07-07T10:00:00Z")));
        repository.append(new ApplicationReviewLog(null, 21013L, ApplicationReviewAction.APPROVE, 1011L, "COLLEGE_REVIEWER", "通过", Instant.parse("2026-07-07T11:00:00Z")));
        repository.append(new ApplicationReviewLog(null, 99999L, ApplicationReviewAction.REJECT, 1011L, "COLLEGE_REVIEWER", "其他申请", Instant.parse("2026-07-07T09:00:00Z")));

        List<ApplicationReviewLog> logs = repository.listByApplicationId(21013L);

        assertThat(logs).hasSize(2);
        assertThat(logs).extracting(ApplicationReviewLog::getAction)
                .containsExactly(ApplicationReviewAction.RETURN, ApplicationReviewAction.APPROVE);
    }

    @Configuration
    @MapperScan(basePackageClasses = ApplicationReviewLogMapper.class)
    @Import({
            MybatisPlusConfig.class,
            MybatisPlusApplicationReviewLogRepository.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:application_review_log_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource, MybatisPlusInterceptor interceptor) throws Exception {
            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factoryBean.setConfiguration(configuration);
            factoryBean.setPlugins(interceptor);
            return factoryBean.getObject();
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ApplicationReviewLogRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because review log domain and mapper classes do not exist.

- [ ] **Step 3: Add domain model and repository interface**

Create `ApplicationReviewAction.java`:

```java
package edu.whut.eval.domain.application.model;

public enum ApplicationReviewAction {
    APPROVE,
    RETURN,
    REJECT
}
```

Create `ApplicationReviewLog.java`:

```java
package edu.whut.eval.domain.application.model;

import java.time.Instant;

public class ApplicationReviewLog {

    private final Long id;
    private final Long applicationId;
    private final ApplicationReviewAction action;
    private final Long reviewerId;
    private final String reviewRole;
    private final String reason;
    private final Instant reviewedAt;

    public ApplicationReviewLog(Long id,
                                Long applicationId,
                                ApplicationReviewAction action,
                                Long reviewerId,
                                String reviewRole,
                                String reason,
                                Instant reviewedAt) {
        this.id = id;
        this.applicationId = applicationId;
        this.action = action;
        this.reviewerId = reviewerId;
        this.reviewRole = reviewRole;
        this.reason = reason;
        this.reviewedAt = reviewedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public ApplicationReviewAction getAction() {
        return action;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public String getReviewRole() {
        return reviewRole;
    }

    public String getReason() {
        return reason;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
```

Create `ApplicationReviewLogRepository.java`:

```java
package edu.whut.eval.domain.application.repository;

import edu.whut.eval.domain.application.model.ApplicationReviewLog;

import java.util.List;

public interface ApplicationReviewLogRepository {

    ApplicationReviewLog append(ApplicationReviewLog reviewLog);

    List<ApplicationReviewLog> listByApplicationId(Long applicationId);
}
```

- [ ] **Step 4: Add mapper and repository implementation**

Create `ApplicationReviewLogDO.java`:

```java
package edu.whut.eval.infra.persistence.dataobject;

import java.time.LocalDateTime;

public class ApplicationReviewLogDO {
    private Long id;
    private Long applicationId;
    private String action;
    private Long reviewerId;
    private String reviewRole;
    private String reason;
    private LocalDateTime reviewedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewRole() {
        return reviewRole;
    }

    public void setReviewRole(String reviewRole) {
        this.reviewRole = reviewRole;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
```

Create `ApplicationReviewLogMapper.java`:

```java
package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.dataobject.ApplicationReviewLogDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApplicationReviewLogMapper {

    @Insert("INSERT INTO application_review_log (application_id, action, reviewer_id, review_role, reason, reviewed_at) VALUES (#{applicationId}, #{action}, #{reviewerId}, #{reviewRole}, #{reason}, #{reviewedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ApplicationReviewLogDO reviewLog);

    @Select("SELECT id, application_id, action, reviewer_id, review_role, reason, reviewed_at FROM application_review_log WHERE application_id = #{applicationId} ORDER BY reviewed_at ASC, id ASC")
    List<ApplicationReviewLogDO> selectByApplicationId(@Param("applicationId") Long applicationId);
}
```

Create `MybatisPlusApplicationReviewLogRepository.java`:

```java
package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.domain.application.model.ApplicationReviewAction;
import edu.whut.eval.domain.application.model.ApplicationReviewLog;
import edu.whut.eval.domain.application.repository.ApplicationReviewLogRepository;
import edu.whut.eval.infra.persistence.dataobject.ApplicationReviewLogDO;
import edu.whut.eval.infra.persistence.mapper.ApplicationReviewLogMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class MybatisPlusApplicationReviewLogRepository implements ApplicationReviewLogRepository {

    private final ApplicationReviewLogMapper applicationReviewLogMapper;

    public MybatisPlusApplicationReviewLogRepository(ApplicationReviewLogMapper applicationReviewLogMapper) {
        this.applicationReviewLogMapper = applicationReviewLogMapper;
    }

    @Override
    public ApplicationReviewLog append(ApplicationReviewLog reviewLog) {
        ApplicationReviewLogDO dataObject = toDataObject(reviewLog);
        applicationReviewLogMapper.insert(dataObject);
        return toDomain(dataObject);
    }

    @Override
    public List<ApplicationReviewLog> listByApplicationId(Long applicationId) {
        return applicationReviewLogMapper.selectByApplicationId(applicationId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private ApplicationReviewLogDO toDataObject(ApplicationReviewLog reviewLog) {
        ApplicationReviewLogDO dataObject = new ApplicationReviewLogDO();
        dataObject.setId(reviewLog.getId());
        dataObject.setApplicationId(reviewLog.getApplicationId());
        dataObject.setAction(reviewLog.getAction().name());
        dataObject.setReviewerId(reviewLog.getReviewerId());
        dataObject.setReviewRole(reviewLog.getReviewRole());
        dataObject.setReason(reviewLog.getReason());
        dataObject.setReviewedAt(toLocalDateTime(reviewLog.getReviewedAt()));
        return dataObject;
    }

    private ApplicationReviewLog toDomain(ApplicationReviewLogDO dataObject) {
        return new ApplicationReviewLog(
                dataObject.getId(),
                dataObject.getApplicationId(),
                ApplicationReviewAction.valueOf(dataObject.getAction()),
                dataObject.getReviewerId(),
                dataObject.getReviewRole(),
                dataObject.getReason(),
                toInstant(dataObject.getReviewedAt())
        );
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.toInstant(ZoneOffset.UTC);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ApplicationReviewLogRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationReviewAction.java whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationReviewLog.java whut-eval-domain/src/main/java/edu/whut/eval/domain/application/repository/ApplicationReviewLogRepository.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/dataobject/ApplicationReviewLogDO.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ApplicationReviewLogMapper.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusApplicationReviewLogRepository.java whut-eval-app/src/test/java/edu/whut/eval/app/review/ApplicationReviewLogRepositoryIntegrationTest.java
git commit -m "feat: persist application review logs"
```

---

### Task 4: Review Read Models and Query Repository

**Files:**
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/query/ReviewApplicationPageQuery.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/repository/ReviewApplicationQueryRepository.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicationQueryRow.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicationAttachmentRow.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ReviewApplicationQueryMapper.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ReviewApplicationQuerySqlProvider.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusReviewApplicationQueryRepository.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationQueryRepositoryIntegrationTest.java`

- [ ] **Step 1: Write failing query repository integration test**

Create `ReviewApplicationQueryRepositoryIntegrationTest.java` with this complete class:

```java
package edu.whut.eval.app.review;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.query.ReviewApplicationPageQuery;
import edu.whut.eval.application.application.repository.ReviewApplicationQueryRepository;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.ReviewApplicationQueryMapper;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import edu.whut.eval.infra.persistence.repository.MybatisPlusReviewApplicationQueryRepository;
import edu.whut.eval.application.auth.service.DefaultAuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.DefaultScopePredicateBuilder;
import edu.whut.eval.application.auth.service.JsonScopeRuleExpressionInterpreter;
import edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ReviewApplicationQueryRepositoryIntegrationTest.TestConfig.class)
class ReviewApplicationQueryRepositoryIntegrationTest {

    private static final String APPLICATION_PERMISSION = "application.review";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReviewApplicationQueryRepository repository;

    @BeforeEach
    void setUpSchemaAndData() {
        recreateTables();
        insertRows();
    }

    @Test
    void shouldFilterReviewListByScopeStatusAcademicYearAndKeyword() {
        PageResult<ReviewApplicationQueryRow> result = repository.pageReviewApplications(
                accessContext(),
                new ReviewApplicationPageQuery(1, 20, "2025-2026", "INTELLECTUAL", null, "SUBMITTED", "20210001", null)
        );

        assertThat(result.total()).isEqualTo(1);
        ReviewApplicationQueryRow row = result.records().get(0);
        assertThat(row.getApplicationId()).isEqualTo(21013L);
        assertThat(row.getApplicantUserName()).isEqualTo("张三");
        assertThat(row.getApplicantUserNo()).isEqualTo("20210001");
        assertThat(row.getOrgUnitName()).isEqualTo("计算机 2101 班");
        assertThat(row.getTitle()).isEqualTo("期刊论文录用申请");
        assertThat(row.getStatus()).isEqualTo("SUBMITTED");
        assertThat(row.getOrgPath()).isEqualTo("/1/2002/2010/");
    }

    @Test
    void shouldLoadReviewDetailResourceWithOrgPathAttachmentsAndScoringSnapshot() {
        ReviewApplicationQueryRow detail = repository.findReviewApplicationDetail(accessContext(), 21013L).orElseThrow();

        assertThat(detail.getApplicationId()).isEqualTo(21013L);
        assertThat(detail.getOrgPath()).isEqualTo("/1/2002/2010/");
        assertThat(detail.getOptionCode()).isEqualTo("PAPER_CORE_FIRST_AUTHOR");
        assertThat(detail.getAppliedPoints()).isEqualByComparingTo("2.00");
        assertThat(detail.getAttachments()).hasSize(1);
        assertThat(detail.getAttachments().get(0).getFileId()).isEqualTo("file-1");
        assertThat(detail.getAttachments().get(0).getStorageKey()).isEqualTo("storage/private/a.pdf");
    }

    private void recreateTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_attachment");
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_fact");
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_submission");
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_user");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_unit");
        jdbcTemplate.execute("CREATE TABLE iam_user (id BIGINT PRIMARY KEY, user_no VARCHAR(64), user_name VARCHAR(128), status VARCHAR(32))");
        jdbcTemplate.execute("CREATE TABLE org_unit (id BIGINT PRIMARY KEY, unit_name VARCHAR(128), path VARCHAR(255), status VARCHAR(32))");
        jdbcTemplate.execute("""
                CREATE TABLE application_submission (
                    application_id BIGINT PRIMARY KEY,
                    applicant_user_id BIGINT NOT NULL,
                    org_unit_id BIGINT NOT NULL,
                    category_code VARCHAR(64) NOT NULL,
                    item_code VARCHAR(64) NOT NULL,
                    academic_year VARCHAR(32) NOT NULL,
                    term VARCHAR(32) NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    description VARCHAR(1000) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    submitted_at DATETIME NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    version BIGINT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE application_attachment (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    application_id BIGINT NOT NULL,
                    file_id VARCHAR(128) NOT NULL,
                    storage_key VARCHAR(512) NOT NULL,
                    original_filename VARCHAR(255) NOT NULL,
                    content_type VARCHAR(128) NOT NULL,
                    size BIGINT NOT NULL,
                    uploaded_by BIGINT NOT NULL,
                    sort_no INT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE application_fact (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    application_id BIGINT NOT NULL,
                    score_value DECIMAL(10,2) NULL,
                    display_text VARCHAR(1000) NULL,
                    evidence_count INT NOT NULL,
                    extra_json VARCHAR(2000) NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL
                )
                """);
    }

    private void insertRows() {
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1001, '20210001', '张三', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1002, '20210002', '李四', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO org_unit (id, unit_name, path, status) VALUES (2010, '计算机 2101 班', '/1/2002/2010/', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO org_unit (id, unit_name, path, status) VALUES (3000, '外部班级', '/1/3000/', 'ACTIVE')");
        jdbcTemplate.update("""
                INSERT INTO application_submission (application_id, applicant_user_id, org_unit_id, category_code, item_code, academic_year, term, title, description, status, submitted_at, created_at, updated_at, version)
                VALUES (21013, 1001, 2010, 'INTELLECTUAL', 'INTELLECTUAL_PAPER', '2025-2026', '上学期', '期刊论文录用申请', '申请说明', 'SUBMITTED', '2026-07-06 10:00:00', '2026-07-06 09:00:00', '2026-07-06 10:00:00', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO application_submission (application_id, applicant_user_id, org_unit_id, category_code, item_code, academic_year, term, title, description, status, submitted_at, created_at, updated_at, version)
                VALUES (21014, 1002, 3000, 'INTELLECTUAL', 'INTELLECTUAL_PAPER', '2025-2026', '上学期', '不可见申请', '申请说明', 'SUBMITTED', '2026-07-06 10:00:00', '2026-07-06 09:00:00', '2026-07-06 10:00:00', 1)
                """);
        jdbcTemplate.update("INSERT INTO application_attachment (application_id, file_id, storage_key, original_filename, content_type, size, uploaded_by, sort_no) VALUES (21013, 'file-1', 'storage/private/a.pdf', 'a.pdf', 'application/pdf', 128, 1001, 0)");
        jdbcTemplate.update("""
                INSERT INTO application_fact (application_id, score_value, display_text, evidence_count, extra_json, created_at, updated_at)
                VALUES (21013, 2.00, NULL, 1, '{"optionCode":"PAPER_CORE_FIRST_AUTHOR","maxPoints":"6.00","exceedsMaxPoints":false}', '2026-07-06 10:00:00', '2026-07-06 10:00:00')
                """);
    }

    private ApplicationAccessContext accessContext() {
        return new ApplicationAccessContext(
                1010L,
                "reviewer-1",
                "Reviewer",
                "COUNSELOR",
                Set.of("COUNSELOR"),
                Set.of(APPLICATION_PERMISSION),
                List.of(new IamScopeRule(1L, APPLICATION_PERMISSION, "ORG_SUBTREE", 2002L, null, null, null, 10, "ACTIVE")),
                APPLICATION_PERMISSION
        );
    }

    @Configuration
    @MapperScan(basePackageClasses = ReviewApplicationQueryMapper.class)
    @Import({
            MybatisPlusConfig.class,
            DefaultAuthorizationScopeEvaluator.class,
            DefaultScopePredicateBuilder.class,
            JsonScopeRuleExpressionInterpreter.class,
            ApplicationScopeSqlTranslator.class,
            MybatisPlusReviewApplicationQueryRepository.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:review_application_query_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource, MybatisPlusInterceptor interceptor) throws Exception {
            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factoryBean.setConfiguration(configuration);
            factoryBean.setPlugins(interceptor);
            return factoryBean.getObject();
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ReviewApplicationQueryRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because review query repository classes do not exist.

- [ ] **Step 3: Add query object and repository interface**

Create `ReviewApplicationPageQuery.java`:

```java
package edu.whut.eval.domain.application.query;

public class ReviewApplicationPageQuery {

    private final long pageNo;
    private final long pageSize;
    private final String academicYear;
    private final String categoryCode;
    private final String itemCode;
    private final String status;
    private final String keyword;
    private final Long orgUnitId;

    public ReviewApplicationPageQuery(long pageNo,
                                      long pageSize,
                                      String academicYear,
                                      String categoryCode,
                                      String itemCode,
                                      String status,
                                      String keyword,
                                      Long orgUnitId) {
        if (pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be greater than 0");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be greater than 0");
        }
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.academicYear = academicYear;
        this.categoryCode = categoryCode;
        this.itemCode = itemCode;
        this.status = status == null || status.isBlank() ? "SUBMITTED" : status;
        this.keyword = keyword;
        this.orgUnitId = orgUnitId;
    }

    public long getPageNo() {
        return pageNo;
    }

    public long getPageSize() {
        return pageSize;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getStatus() {
        return status;
    }

    public String getKeyword() {
        return keyword;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public long getOffset() {
        return (pageNo - 1) * pageSize;
    }
}
```

Create `ReviewApplicationQueryRepository.java`:

```java
package edu.whut.eval.application.application.repository;

import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.query.ReviewApplicationPageQuery;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;

import java.util.Optional;

public interface ReviewApplicationQueryRepository {

    PageResult<ReviewApplicationQueryRow> pageReviewApplications(ApplicationAccessContext accessContext,
                                                                 ReviewApplicationPageQuery query);

    Optional<ReviewApplicationQueryRow> findReviewApplicationDetail(ApplicationAccessContext accessContext,
                                                                    Long applicationId);
}
```

- [ ] **Step 4: Add query row classes**

Create `ReviewApplicationAttachmentRow.java`:

```java
package edu.whut.eval.application.application.query;

public class ReviewApplicationAttachmentRow {
    private String fileId;
    private String storageKey;
    private String originalFilename;
    private String contentType;
    private Long size;
    private Integer sortNo;

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}
```

Create `ReviewApplicationQueryRow.java`:

```java
package edu.whut.eval.application.application.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ReviewApplicationQueryRow {
    private Long applicationId;
    private Long applicantUserId;
    private String applicantUserName;
    private String applicantUserNo;
    private Long orgUnitId;
    private String orgUnitName;
    private String orgPath;
    private String categoryCode;
    private String itemCode;
    private String academicYear;
    private String term;
    private String title;
    private String description;
    private String status;
    private LocalDateTime submittedAt;
    private Long version;
    private String optionCode;
    private BigDecimal appliedPoints;
    private BigDecimal maxPoints;
    private Integer evidenceCount;
    private Boolean exceedsMaxPoints;
    private String warningMessage;
    private String extraJson;
    private List<ReviewApplicationAttachmentRow> attachments = List.of();

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getApplicantUserId() {
        return applicantUserId;
    }

    public void setApplicantUserId(Long applicantUserId) {
        this.applicantUserId = applicantUserId;
    }

    public String getApplicantUserName() {
        return applicantUserName;
    }

    public void setApplicantUserName(String applicantUserName) {
        this.applicantUserName = applicantUserName;
    }

    public String getApplicantUserNo() {
        return applicantUserNo;
    }

    public void setApplicantUserNo(String applicantUserNo) {
        this.applicantUserNo = applicantUserNo;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(Long orgUnitId) {
        this.orgUnitId = orgUnitId;
    }

    public String getOrgUnitName() {
        return orgUnitName;
    }

    public void setOrgUnitName(String orgUnitName) {
        this.orgUnitName = orgUnitName;
    }

    public String getOrgPath() {
        return orgPath;
    }

    public void setOrgPath(String orgPath) {
        this.orgPath = orgPath;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getOptionCode() {
        return optionCode;
    }

    public void setOptionCode(String optionCode) {
        this.optionCode = optionCode;
    }

    public BigDecimal getAppliedPoints() {
        return appliedPoints;
    }

    public void setAppliedPoints(BigDecimal appliedPoints) {
        this.appliedPoints = appliedPoints;
    }

    public BigDecimal getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(BigDecimal maxPoints) {
        this.maxPoints = maxPoints;
    }

    public Integer getEvidenceCount() {
        return evidenceCount;
    }

    public void setEvidenceCount(Integer evidenceCount) {
        this.evidenceCount = evidenceCount;
    }

    public Boolean getExceedsMaxPoints() {
        return exceedsMaxPoints;
    }

    public void setExceedsMaxPoints(Boolean exceedsMaxPoints) {
        this.exceedsMaxPoints = exceedsMaxPoints;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public void setWarningMessage(String warningMessage) {
        this.warningMessage = warningMessage;
    }

    public String getExtraJson() {
        return extraJson;
    }

    public void setExtraJson(String extraJson) {
        this.extraJson = extraJson;
    }

    public List<ReviewApplicationAttachmentRow> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ReviewApplicationAttachmentRow> attachments) {
        this.attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
```

- [ ] **Step 5: Add mapper and SQL provider**

Create `ReviewApplicationQueryMapper.java`:

```java
package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.domain.application.query.ReviewApplicationPageQuery;
import edu.whut.eval.application.application.query.ReviewApplicationAttachmentRow;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReviewApplicationQueryMapper {

    @SelectProvider(type = ReviewApplicationQuerySqlProvider.class, method = "buildCountReviewApplications")
    long countReviewApplications(@Param("expression") String expression,
                                 @Param("parameters") Map<String, Object> parameters,
                                 @Param("query") ReviewApplicationPageQuery query);

    @SelectProvider(type = ReviewApplicationQuerySqlProvider.class, method = "buildSelectReviewApplications")
    List<ReviewApplicationQueryRow> selectReviewApplications(@Param("expression") String expression,
                                                             @Param("parameters") Map<String, Object> parameters,
                                                             @Param("query") ReviewApplicationPageQuery query,
                                                             @Param("offset") long offset,
                                                             @Param("limit") long limit);

    @SelectProvider(type = ReviewApplicationQuerySqlProvider.class, method = "buildSelectReviewApplicationDetail")
    ReviewApplicationQueryRow selectReviewApplicationDetail(@Param("expression") String expression,
                                                            @Param("parameters") Map<String, Object> parameters,
                                                            @Param("applicationId") Long applicationId);

    @Select("SELECT file_id AS fileId, storage_key AS storageKey, original_filename AS originalFilename, content_type AS contentType, size, sort_no AS sortNo FROM application_attachment WHERE application_id = #{applicationId} ORDER BY sort_no ASC, id ASC")
    List<ReviewApplicationAttachmentRow> selectAttachments(@Param("applicationId") Long applicationId);
}
```

Create `ReviewApplicationQuerySqlProvider.java`:

```java
package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.domain.application.query.ReviewApplicationPageQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReviewApplicationQuerySqlProvider {

    private static final String REVIEW_BASE = """
            FROM (
                SELECT s.application_id AS application_id,
                       s.applicant_user_id AS applicant_user_id,
                       u.user_name AS user_name,
                       u.user_no AS user_no,
                       s.org_unit_id AS org_unit_id,
                       o.unit_name AS org_unit_name,
                       o.path AS org_path,
                       s.category_code AS category_code,
                       s.item_code AS item_code,
                       s.academic_year AS academic_year,
                       s.term AS term,
                       s.title AS title,
                       s.description AS description,
                       s.status AS status,
                       s.submitted_at AS submitted_at,
                       s.version AS version,
                       f.score_value AS score_value,
                       f.evidence_count AS evidence_count,
                       f.extra_json AS extra_json,
                       f.display_text AS display_text
                FROM application_submission s
                JOIN iam_user u ON u.id = s.applicant_user_id
                JOIN org_unit o ON o.id = s.org_unit_id
                LEFT JOIN application_fact f ON f.application_id = s.application_id
            ) review_base
            """;

    public String buildCountReviewApplications(Map<String, Object> params) {
        return buildSql("SELECT COUNT(1) " + REVIEW_BASE, params, false, false);
    }

    public String buildSelectReviewApplications(Map<String, Object> params) {
        return buildSql(selectColumns() + REVIEW_BASE, params, true, false);
    }

    public String buildSelectReviewApplicationDetail(Map<String, Object> params) {
        return buildSql(selectColumns() + REVIEW_BASE, params, false, true);
    }

    private String buildSql(String selectFromSql, Map<String, Object> params, boolean paged, boolean detail) {
        String expression = params == null ? "" : (String) params.get("expression");
        ReviewApplicationPageQuery query = params == null ? null : (ReviewApplicationPageQuery) params.get("query");
        List<String> conditions = new ArrayList<>();
        if (expression != null && !expression.isBlank()) {
            conditions.add("(" + expression + ")");
        }
        if (detail) {
            conditions.add("application_id = #{applicationId}");
        } else {
            appendQueryFilters(conditions, query);
        }
        StringBuilder sql = new StringBuilder(selectFromSql);
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        if (paged) {
            sql.append(" ORDER BY submitted_at ASC, application_id ASC");
            sql.append(" LIMIT #{limit} OFFSET #{offset}");
        }
        return sql.toString();
    }

    private String selectColumns() {
        return """
                SELECT application_id AS applicationId,
                       applicant_user_id AS applicantUserId,
                       user_name AS applicantUserName,
                       user_no AS applicantUserNo,
                       org_unit_id AS orgUnitId,
                       org_unit_name AS orgUnitName,
                       org_path AS orgPath,
                       category_code AS categoryCode,
                       item_code AS itemCode,
                       academic_year AS academicYear,
                       term AS term,
                       title AS title,
                       description AS description,
                       status AS status,
                       submitted_at AS submittedAt,
                       version AS version,
                       score_value AS appliedPoints,
                       evidence_count AS evidenceCount,
                       extra_json AS extraJson,
                       display_text AS warningMessage
                """;
    }

    private void appendQueryFilters(List<String> conditions, ReviewApplicationPageQuery query) {
        if (query == null) {
            conditions.add("status = 'SUBMITTED'");
            return;
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            conditions.add("status = #{query.status}");
        }
        if (query.getAcademicYear() != null && !query.getAcademicYear().isBlank()) {
            conditions.add("academic_year = #{query.academicYear}");
        }
        if (query.getCategoryCode() != null && !query.getCategoryCode().isBlank()) {
            conditions.add("category_code = #{query.categoryCode}");
        }
        if (query.getItemCode() != null && !query.getItemCode().isBlank()) {
            conditions.add("item_code = #{query.itemCode}");
        }
        if (query.getOrgUnitId() != null) {
            conditions.add("org_unit_id = #{query.orgUnitId}");
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            conditions.add("(user_name LIKE CONCAT('%', #{query.keyword}, '%') OR user_no LIKE CONCAT('%', #{query.keyword}, '%'))");
        }
    }
}
```

The SQL intentionally projects an inner `review_base` relation with column names matching `ApplicationScopeSqlTranslator` (`application_id`, `applicant_user_id`, `org_unit_id`, `org_path`, `category_code`, `item_code`). Keep scope expressions and business filters on these unqualified outer column names; do not reference `s.`/`u.`/`o.` aliases in the outer `WHERE`. It also selects `extra_json AS extraJson` instead of using MySQL JSON functions. Parse `optionCode`, `maxPoints`, and `exceedsMaxPoints` in the repository so the same code works in MySQL and H2 MySQL-mode tests.

- [ ] **Step 6: Add repository implementation**

Create `MybatisPlusReviewApplicationQueryRepository.java`:

```java
package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.query.ReviewApplicationPageQuery;
import edu.whut.eval.application.application.repository.ReviewApplicationQueryRepository;
import edu.whut.eval.domain.auth.model.ApplicationScopePredicate;
import edu.whut.eval.domain.auth.model.AuthorizationScopeSet;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.auth.service.AuthorizationScopeEvaluator;
import edu.whut.eval.domain.auth.service.ScopePredicateBuilder;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.persistence.mapper.ReviewApplicationQueryMapper;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator;
import edu.whut.eval.infra.security.sql.SqlPredicateFragment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class MybatisPlusReviewApplicationQueryRepository implements ReviewApplicationQueryRepository {

    private final AuthorizationScopeEvaluator authorizationScopeEvaluator;
    private final ScopePredicateBuilder scopePredicateBuilder;
    private final ApplicationScopeSqlTranslator applicationScopeSqlTranslator;
    private final ReviewApplicationQueryMapper reviewApplicationQueryMapper;
    private final ObjectMapper objectMapper;

    public MybatisPlusReviewApplicationQueryRepository(AuthorizationScopeEvaluator authorizationScopeEvaluator,
                                                       ScopePredicateBuilder scopePredicateBuilder,
                                                       ApplicationScopeSqlTranslator applicationScopeSqlTranslator,
                                                       ReviewApplicationQueryMapper reviewApplicationQueryMapper,
                                                       ObjectMapper objectMapper) {
        this.authorizationScopeEvaluator = authorizationScopeEvaluator;
        this.scopePredicateBuilder = scopePredicateBuilder;
        this.applicationScopeSqlTranslator = applicationScopeSqlTranslator;
        this.reviewApplicationQueryMapper = reviewApplicationQueryMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<ReviewApplicationQueryRow> pageReviewApplications(ApplicationAccessContext accessContext,
                                                                        ReviewApplicationPageQuery query) {
        SqlPredicateFragment fragment = scopeFragment(accessContext);
        long total = reviewApplicationQueryMapper.countReviewApplications(fragment.getExpression(), fragment.getParameters(), query);
        List<ReviewApplicationQueryRow> records = reviewApplicationQueryMapper.selectReviewApplications(
                fragment.getExpression(),
                fragment.getParameters(),
                query,
                query.getOffset(),
                query.getPageSize()
        ).stream().peek(this::materializeScoringSnapshot).toList();
        return new PageResult<>(total, records);
    }

    @Override
    public Optional<ReviewApplicationQueryRow> findReviewApplicationDetail(ApplicationAccessContext accessContext,
                                                                          Long applicationId) {
        SqlPredicateFragment fragment = scopeFragment(accessContext);
        ReviewApplicationQueryRow row = reviewApplicationQueryMapper.selectReviewApplicationDetail(
                fragment.getExpression(),
                fragment.getParameters(),
                applicationId
        );
        if (row == null) {
            return Optional.empty();
        }
        materializeScoringSnapshot(row);
        row.setAttachments(reviewApplicationQueryMapper.selectAttachments(applicationId));
        return Optional.of(row);
    }

    private SqlPredicateFragment scopeFragment(ApplicationAccessContext accessContext) {
        UserAuthorizationContext authorizationContext = new UserAuthorizationContext(
                accessContext.getUserId(),
                accessContext.getUserNo(),
                accessContext.getUserName(),
                accessContext.getIdentity(),
                accessContext.getRoles(),
                accessContext.getAuthorities(),
                accessContext.getScopeRules()
        );
        AuthorizationScopeSet scopeSet = authorizationScopeEvaluator.evaluate(authorizationContext, accessContext.getPermissionCode());
        ApplicationScopePredicate predicate = scopePredicateBuilder.buildForApplication(authorizationContext, scopeSet);
        return applicationScopeSqlTranslator.translate(authorizationContext, predicate);
    }

    private void materializeScoringSnapshot(ReviewApplicationQueryRow row) {
        if (row.getExtraJson() == null || row.getExtraJson().isBlank()) {
            return;
        }
        try {
            JsonNode extra = objectMapper.readTree(row.getExtraJson());
            row.setOptionCode(extra.path("optionCode").asText(null));
            String maxPointsText = extra.path("maxPoints").asText(null);
            row.setMaxPoints(maxPointsText == null || maxPointsText.isBlank() ? null : new BigDecimal(maxPointsText));
            row.setExceedsMaxPoints(extra.path("exceedsMaxPoints").asBoolean(false));
        } catch (Exception exception) {
            throw new IllegalStateException("申请评分快照解析失败: applicationId=" + row.getApplicationId(), exception);
        }
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ReviewApplicationQueryRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add whut-eval-domain/src/main/java/edu/whut/eval/domain/application/query/ReviewApplicationPageQuery.java whut-eval-application/src/main/java/edu/whut/eval/application/application/repository/ReviewApplicationQueryRepository.java whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicationQueryRow.java whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicationAttachmentRow.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ReviewApplicationQueryMapper.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ReviewApplicationQuerySqlProvider.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusReviewApplicationQueryRepository.java whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationQueryRepositoryIntegrationTest.java
git commit -m "feat: add review application query repository"
```

---

### Task 5: Single-Application Scope Validator

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ReviewApplicationAccessValidator.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationAccessValidatorTest.java`

- [ ] **Step 1: Write failing validator tests**

Create `ReviewApplicationAccessValidatorTest.java`:

```java
package edu.whut.eval.app.review;

import edu.whut.eval.application.application.service.ReviewApplicationAccessValidator;
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.model.ApplicationResourceContext;
import edu.whut.eval.application.auth.model.ScopeAccessDecision;
import edu.whut.eval.application.auth.service.ResourceScopeAccessEvaluator;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReviewApplicationAccessValidatorTest {

    private final ResourceScopeAccessEvaluator evaluator = mock(ResourceScopeAccessEvaluator.class);
    private final ReviewApplicationAccessValidator validator = new ReviewApplicationAccessValidator(evaluator);

    @Test
    void shouldAllowWhenResourceEvaluatorAllowsOrgSubtreeContext() {
        UserAuthorizationContext reviewer = reviewer();
        ReviewApplicationQueryRow row = rowWithOrgPath("/1/2002/2010/");
        given(evaluator.canAccessApplication(eq(reviewer), eq(AuthorizationPermissionCodes.APPLICATION_REVIEW), any()))
                .willReturn(ScopeAccessDecision.allow("ORG_SUBTREE", "matched scope rule"));

        validator.requireAccess(reviewer, row);

        ArgumentCaptor<ApplicationResourceContext> resourceCaptor = forClass(ApplicationResourceContext.class);
        verify(evaluator).canAccessApplication(
                eq(reviewer),
                eq(AuthorizationPermissionCodes.APPLICATION_REVIEW),
                resourceCaptor.capture()
        );
        assertThat(resourceCaptor.getValue().getApplicationId()).isEqualTo(21013L);
        assertThat(resourceCaptor.getValue().getApplicantUserId()).isEqualTo(1001L);
        assertThat(resourceCaptor.getValue().getOrgUnitId()).isEqualTo(2010L);
        assertThat(resourceCaptor.getValue().getOrgPath()).isEqualTo("/1/2002/2010/");
        assertThat(resourceCaptor.getValue().getCategoryCode()).isEqualTo("INTELLECTUAL");
        assertThat(resourceCaptor.getValue().getItemCode()).isEqualTo("INTELLECTUAL_PAPER");
    }

    @Test
    void shouldDenyWhenResourceEvaluatorDenies() {
        UserAuthorizationContext reviewer = reviewer();
        ReviewApplicationQueryRow row = rowWithOrgPath("/1/3000/");
        given(evaluator.canAccessApplication(eq(reviewer), eq(AuthorizationPermissionCodes.APPLICATION_REVIEW), any()))
                .willReturn(ScopeAccessDecision.deny("no-scope-matched"));

        assertThatThrownBy(() -> validator.requireAccess(reviewer, row))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前审核人无权访问该申请");
    }

    private UserAuthorizationContext reviewer() {
        return new UserAuthorizationContext(
                1010L,
                "reviewer-1",
                "Reviewer",
                "COUNSELOR",
                Set.of("COUNSELOR"),
                Set.of(AuthorizationPermissionCodes.APPLICATION_REVIEW),
                List.of()
        );
    }

    private ReviewApplicationQueryRow rowWithOrgPath(String orgPath) {
        ReviewApplicationQueryRow row = new ReviewApplicationQueryRow();
        row.setApplicationId(21013L);
        row.setApplicantUserId(1001L);
        row.setOrgUnitId(2010L);
        row.setOrgPath(orgPath);
        row.setCategoryCode("INTELLECTUAL");
        row.setItemCode("INTELLECTUAL_PAPER");
        return row;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ReviewApplicationAccessValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `ReviewApplicationAccessValidator` does not exist.

- [ ] **Step 3: Implement validator**

Create `ReviewApplicationAccessValidator.java`:

```java
package edu.whut.eval.application.application.service;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.model.ApplicationResourceContext;
import edu.whut.eval.application.auth.model.ScopeAccessDecision;
import edu.whut.eval.application.auth.service.ResourceScopeAccessEvaluator;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import org.springframework.stereotype.Service;

@Service
public class ReviewApplicationAccessValidator {

    private final ResourceScopeAccessEvaluator resourceScopeAccessEvaluator;

    public ReviewApplicationAccessValidator(ResourceScopeAccessEvaluator resourceScopeAccessEvaluator) {
        this.resourceScopeAccessEvaluator = resourceScopeAccessEvaluator;
    }

    public void requireAccess(UserAuthorizationContext reviewer, ReviewApplicationQueryRow row) {
        ScopeAccessDecision decision = resourceScopeAccessEvaluator.canAccessApplication(
                reviewer,
                AuthorizationPermissionCodes.APPLICATION_REVIEW,
                new ApplicationResourceContext(
                        row.getApplicationId(),
                        row.getApplicantUserId(),
                        row.getOrgUnitId(),
                        row.getOrgPath(),
                        row.getCategoryCode(),
                        row.getItemCode()
                )
        );
        if (!decision.isAllowed()) {
            throw new AccessDeniedAppException("当前审核人无权访问该申请");
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ReviewApplicationAccessValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ReviewApplicationAccessValidator.java whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationAccessValidatorTest.java
git commit -m "feat: validate review application scope"
```

---

### Task 6: Review Command Service

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/command/ApproveReviewCommand.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/command/ReturnReviewCommand.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/command/RejectReviewCommand.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewActionResultView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ReviewApplicationCommandApplicationService.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationCommandApplicationServiceTest.java`

- [ ] **Step 1: Write failing command service tests**

Create `ReviewApplicationCommandApplicationServiceTest.java`:

```java
package edu.whut.eval.app.review;

import edu.whut.eval.application.application.command.ApproveReviewCommand;
import edu.whut.eval.application.application.command.ReturnReviewCommand;
import edu.whut.eval.application.application.query.ReviewActionResultView;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import edu.whut.eval.application.application.repository.ReviewApplicationQueryRepository;
import edu.whut.eval.application.application.service.ReviewApplicationAccessValidator;
import edu.whut.eval.application.application.service.ReviewApplicationCommandApplicationService;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.application.model.ApplicationReviewAction;
import edu.whut.eval.domain.application.model.ApplicationReviewLog;
import edu.whut.eval.domain.application.model.ApplicationScoringSnapshot;
import edu.whut.eval.domain.application.model.ApplicationSubmission;
import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;
import edu.whut.eval.domain.application.model.AttachmentRef;
import edu.whut.eval.domain.application.repository.ApplicationReviewLogRepository;
import edu.whut.eval.domain.application.repository.ApplicationSubmissionRepository;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ReviewApplicationCommandApplicationServiceTest {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final ReviewApplicationQueryRepository reviewApplicationQueryRepository = mock(ReviewApplicationQueryRepository.class);
    private final ReviewApplicationAccessValidator reviewApplicationAccessValidator = mock(ReviewApplicationAccessValidator.class);
    private final ApplicationSubmissionRepository applicationSubmissionRepository = mock(ApplicationSubmissionRepository.class);
    private final ApplicationReviewLogRepository applicationReviewLogRepository = mock(ApplicationReviewLogRepository.class);

    private final ReviewApplicationCommandApplicationService service = new ReviewApplicationCommandApplicationService(
            userAuthorizationContextAssembler,
            reviewApplicationQueryRepository,
            reviewApplicationAccessValidator,
            applicationSubmissionRepository,
            applicationReviewLogRepository
    );

@Test
void shouldApproveSubmittedApplicationAppendLogAndReturnGeneratedLogId() {
    given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());
    given(reviewApplicationQueryRepository.findReviewApplicationDetail(any(), any())).willReturn(Optional.of(resourceRow()));
    given(applicationSubmissionRepository.findById(21013L)).willReturn(Optional.of(submittedApplication()));
    given(applicationSubmissionRepository.save(any(ApplicationSubmission.class))).willAnswer(invocation -> invocation.getArgument(0));
    given(applicationReviewLogRepository.append(any(ApplicationReviewLog.class))).willAnswer(invocation -> {
        ApplicationReviewLog log = invocation.getArgument(0);
        return new ApplicationReviewLog(31001L, log.getApplicationId(), log.getAction(), log.getReviewerId(),
                log.getReviewRole(), log.getReason(), log.getReviewedAt());
    });

    ReviewActionResultView result = service.approve(new ApproveReviewCommand(21013L, 1L, "同意"));

    assertThat(result.applicationId()).isEqualTo(21013L);
    assertThat(result.status()).isEqualTo(ApplicationSubmissionStatus.APPROVED);
    assertThat(result.version()).isEqualTo(2L);
    assertThat(result.reviewLogId()).isEqualTo(31001L);
    ArgumentCaptor<ApplicationSubmission> submissionCaptor = forClass(ApplicationSubmission.class);
    verify(applicationSubmissionRepository).save(submissionCaptor.capture());
    assertThat(submissionCaptor.getValue().getScoringSnapshot()).isNotNull();
    assertThat(submissionCaptor.getValue().getScoringSnapshot().optionCode()).isEqualTo("OPTION_A");
    ArgumentCaptor<ApplicationReviewLog> logCaptor = forClass(ApplicationReviewLog.class);
    verify(applicationReviewLogRepository).append(logCaptor.capture());
    assertThat(logCaptor.getValue().getAction()).isEqualTo(ApplicationReviewAction.APPROVE);
    assertThat(logCaptor.getValue().getReason()).isEqualTo("同意");
}

@Test
void shouldRejectBlankReasonForReturn() {
    assertThatThrownBy(() -> service.returnForFix(new ReturnReviewCommand(21013L, 1L, " ")))
            .isInstanceOf(ValidationException.class)
            .hasMessage("reason 不能为空");
}

@Test
void shouldRejectOutOfScopeApplication() {
    given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());
    given(reviewApplicationQueryRepository.findReviewApplicationDetail(any(), any())).willReturn(Optional.of(resourceRow()));
    willThrow(new AccessDeniedAppException("当前审核人无权访问该申请"))
            .given(reviewApplicationAccessValidator).requireAccess(any(), any());

    assertThatThrownBy(() -> service.approve(new ApproveReviewCommand(21013L, 1L, null)))
            .isInstanceOf(AccessDeniedAppException.class)
            .hasMessage("当前审核人无权访问该申请");
    verifyNoInteractions(applicationSubmissionRepository, applicationReviewLogRepository);
}

@Test
void shouldDeclareTransactionalBoundaryOnReviewActions() throws Exception {
    Method approve = ReviewApplicationCommandApplicationService.class.getMethod("approve", ApproveReviewCommand.class);
    Method returnForFix = ReviewApplicationCommandApplicationService.class.getMethod("returnForFix", ReturnReviewCommand.class);

    assertThat(approve.isAnnotationPresent(Transactional.class)).isTrue();
    assertThat(returnForFix.isAnnotationPresent(Transactional.class)).isTrue();
}

private UserAuthorizationContext reviewer() {
    return new UserAuthorizationContext(
            1010L,
            "A0010",
            "Counselor",
            "COUNSELOR",
            Set.of("COUNSELOR"),
            Set.of("application.review"),
            List.of()
    );
}

private ReviewApplicationQueryRow resourceRow() {
    ReviewApplicationQueryRow row = new ReviewApplicationQueryRow();
    row.setApplicationId(21013L);
    row.setApplicantUserId(1001L);
    row.setOrgUnitId(2010L);
    row.setOrgPath("/WHUT/CS/CLASS1");
    row.setCategoryCode("INTELLECTUAL");
    row.setItemCode("INTELLECTUAL_PAPER");
    row.setStatus("SUBMITTED");
    return row;
}

private ApplicationSubmission submittedApplication() {
    return new ApplicationSubmission(
            21013L,
            1001L,
            2010L,
            "INTELLECTUAL",
            "INTELLECTUAL_PAPER",
            "2025-2026",
            "上学期",
            "论文申请",
            "申请说明",
            List.of(new AttachmentRef("file-1", "uploads/a.pdf", "a.pdf", "application/pdf", 128L, 1001L)),
            ApplicationSubmissionStatus.SUBMITTED,
            Instant.parse("2026-07-07T10:00:00Z"),
            Instant.parse("2026-07-07T09:00:00Z"),
            Instant.parse("2026-07-07T10:00:00Z"),
            1L,
            new ApplicationScoringSnapshot("OPTION_A", new BigDecimal("2.00"), new BigDecimal("6.00"), 1, false, null)
    );
}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ReviewApplicationCommandApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because command service classes do not exist.

- [ ] **Step 3: Add command and result classes**

Create `ApproveReviewCommand.java`:

```java
package edu.whut.eval.application.application.command;

public record ApproveReviewCommand(Long applicationId, Long expectedVersion, String comment) {
}
```

Create `ReturnReviewCommand.java`:

```java
package edu.whut.eval.application.application.command;

public record ReturnReviewCommand(Long applicationId, Long expectedVersion, String reason) {
}
```

Create `RejectReviewCommand.java`:

```java
package edu.whut.eval.application.application.command;

public record RejectReviewCommand(Long applicationId, Long expectedVersion, String reason) {
}
```

Create `ReviewActionResultView.java`:

```java
package edu.whut.eval.application.application.query;

import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;

import java.time.Instant;

public record ReviewActionResultView(Long applicationId,
                                     ApplicationSubmissionStatus status,
                                     Long version,
                                     Long reviewLogId,
                                     Instant reviewedAt) {
}
```

- [ ] **Step 4: Implement command service**

Create `ReviewApplicationCommandApplicationService.java`:

```java
package edu.whut.eval.application.application.service;

import edu.whut.eval.application.application.command.ApproveReviewCommand;
import edu.whut.eval.application.application.command.RejectReviewCommand;
import edu.whut.eval.application.application.command.ReturnReviewCommand;
import edu.whut.eval.application.application.query.ReviewActionResultView;
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.application.model.ApplicationReviewAction;
import edu.whut.eval.domain.application.model.ApplicationReviewLog;
import edu.whut.eval.domain.application.model.ApplicationSubmission;
import edu.whut.eval.domain.application.repository.ApplicationReviewLogRepository;
import edu.whut.eval.domain.application.repository.ApplicationSubmissionRepository;
import edu.whut.eval.application.application.repository.ReviewApplicationQueryRepository;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;

@Service
public class ReviewApplicationCommandApplicationService {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final ReviewApplicationQueryRepository reviewApplicationQueryRepository;
    private final ReviewApplicationAccessValidator reviewApplicationAccessValidator;
    private final ApplicationSubmissionRepository applicationSubmissionRepository;
    private final ApplicationReviewLogRepository applicationReviewLogRepository;

    public ReviewApplicationCommandApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                                      ReviewApplicationQueryRepository reviewApplicationQueryRepository,
                                                      ReviewApplicationAccessValidator reviewApplicationAccessValidator,
                                                      ApplicationSubmissionRepository applicationSubmissionRepository,
                                                      ApplicationReviewLogRepository applicationReviewLogRepository) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.reviewApplicationQueryRepository = reviewApplicationQueryRepository;
        this.reviewApplicationAccessValidator = reviewApplicationAccessValidator;
        this.applicationSubmissionRepository = applicationSubmissionRepository;
        this.applicationReviewLogRepository = applicationReviewLogRepository;
    }

    @Transactional
    public ReviewActionResultView approve(ApproveReviewCommand command) {
        return review(command.applicationId(), command.expectedVersion(), ApplicationReviewAction.APPROVE, command.comment());
    }

    @Transactional
    public ReviewActionResultView returnForFix(ReturnReviewCommand command) {
        requireReason(command.reason());
        return review(command.applicationId(), command.expectedVersion(), ApplicationReviewAction.RETURN, command.reason());
    }

    @Transactional
    public ReviewActionResultView reject(RejectReviewCommand command) {
        requireReason(command.reason());
        return review(command.applicationId(), command.expectedVersion(), ApplicationReviewAction.REJECT, command.reason());
    }

    private ReviewActionResultView review(Long applicationId,
                                          Long expectedVersion,
                                          ApplicationReviewAction action,
                                          String reason) {
        UserAuthorizationContext reviewer = userAuthorizationContextAssembler.requiredAuthorizationContext();
        if (!reviewer.hasAuthority(AuthorizationPermissionCodes.APPLICATION_REVIEW)) {
            throw new AccessDeniedAppException("当前审核人无审核权限");
        }
        ReviewApplicationQueryRow resource = reviewApplicationQueryRepository.findReviewApplicationDetail(toAccessContext(reviewer), applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在"));
        reviewApplicationAccessValidator.requireAccess(reviewer, resource);
        ApplicationSubmission submission = applicationSubmissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在"));
        ApplicationSubmission reviewed = switch (action) {
            case APPROVE -> submission.approve(requiredExpectedVersion(expectedVersion));
            case RETURN -> submission.returnForFix(requiredExpectedVersion(expectedVersion));
            case REJECT -> submission.reject(requiredExpectedVersion(expectedVersion));
        };
        ApplicationSubmission saved = applicationSubmissionRepository.save(reviewed);
        Instant reviewedAt = Instant.now();
        ApplicationReviewLog log = applicationReviewLogRepository.append(new ApplicationReviewLog(
                null,
                applicationId,
                action,
                reviewer.getUserId(),
                resolveReviewRole(reviewer),
                normalizeReason(reason),
                reviewedAt
        ));
        return new ReviewActionResultView(saved.getApplicationId(), saved.getStatus(), saved.getVersion(), log.getId(), log.getReviewedAt());
    }

    private ApplicationAccessContext toAccessContext(UserAuthorizationContext reviewer) {
        return new ApplicationAccessContext(
                reviewer.getUserId(),
                reviewer.getUserNo(),
                reviewer.getUserName(),
                reviewer.getIdentity(),
                reviewer.getRoles(),
                reviewer.getAuthorities(),
                reviewer.getScopeRules(),
                AuthorizationPermissionCodes.APPLICATION_REVIEW
        );
    }

    private long requiredExpectedVersion(Long expectedVersion) {
        if (expectedVersion == null) {
            throw new ValidationException("expectedVersion 不能为空");
        }
        return expectedVersion;
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("reason 不能为空");
        }
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private String resolveReviewRole(UserAuthorizationContext reviewer) {
        if (reviewer.getIdentity() != null && !reviewer.getIdentity().isBlank()) {
            return reviewer.getIdentity();
        }
        return reviewer.getRoles().stream()
                .filter(role -> role != null && !role.isBlank())
                .sorted(Comparator.naturalOrder())
                .findFirst()
                .orElse("UNKNOWN");
    }
}
```

- [ ] **Step 5: Run command service tests**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ReviewApplicationCommandApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/application/command/ApproveReviewCommand.java whut-eval-application/src/main/java/edu/whut/eval/application/application/command/ReturnReviewCommand.java whut-eval-application/src/main/java/edu/whut/eval/application/application/command/RejectReviewCommand.java whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewActionResultView.java whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ReviewApplicationCommandApplicationService.java whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationCommandApplicationServiceTest.java
git commit -m "feat: add review action service"
```

---

### Task 7: Review Query Application Service and DTO Mapping

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicationListItemView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicationDetailView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicationSummaryView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicantView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewScoringSnapshotView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewLogView.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ReviewApplicationQueryApplicationService.java`
- Create tests inside `whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationQueryApplicationServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Create `ReviewApplicationQueryApplicationServiceTest.java`:

```java
package edu.whut.eval.app.review;

import edu.whut.eval.application.application.query.ReviewApplicationDetailView;
import edu.whut.eval.application.application.query.ReviewApplicationListItemView;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import edu.whut.eval.application.application.repository.ReviewApplicationQueryRepository;
import edu.whut.eval.application.application.service.ReviewApplicationAccessValidator;
import edu.whut.eval.application.application.service.ReviewApplicationQueryApplicationService;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.domain.application.model.ApplicationReviewAction;
import edu.whut.eval.domain.application.model.ApplicationReviewLog;
import edu.whut.eval.domain.application.query.ReviewApplicationPageQuery;
import edu.whut.eval.domain.application.repository.ApplicationReviewLogRepository;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.shared.PageResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReviewApplicationQueryApplicationServiceTest {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final ReviewApplicationQueryRepository reviewApplicationQueryRepository = mock(ReviewApplicationQueryRepository.class);
    private final ReviewApplicationAccessValidator reviewApplicationAccessValidator = mock(ReviewApplicationAccessValidator.class);
    private final ApplicationReviewLogRepository applicationReviewLogRepository = mock(ApplicationReviewLogRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ReviewApplicationQueryApplicationService service = new ReviewApplicationQueryApplicationService(
            userAuthorizationContextAssembler,
            reviewApplicationQueryRepository,
            reviewApplicationAccessValidator,
            applicationReviewLogRepository
    );

@Test
void shouldReturnDedicatedReviewListView() {
    given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());
    given(reviewApplicationQueryRepository.pageReviewApplications(any(), any()))
            .willReturn(new PageResult<>(1, List.of(submittedRow())));

    PageResult<ReviewApplicationListItemView> result = service.pageReviewApplications(
            new ReviewApplicationPageQuery(1, 20, "2025-2026", "INTELLECTUAL", "INTELLECTUAL_PAPER", "SUBMITTED", "论文", 2010L)
    );

    assertThat(result.total()).isEqualTo(1);
    ReviewApplicationListItemView item = result.records().get(0);
    assertThat(item.applicationId()).isEqualTo(21013L);
    assertThat(item.applicantUserName()).isEqualTo("张三");
    assertThat(item.orgUnitName()).isEqualTo("计算机学院 1 班");
    assertThat(item.currentReviewNode()).isEqualTo("SINGLE_REVIEW");
}

@Test
void shouldReturnDetailWithC4TopLevelShapeLogsAndAllowedActions() throws Exception {
    given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());
    given(reviewApplicationQueryRepository.findReviewApplicationDetail(any(), any())).willReturn(Optional.of(submittedRow()));
    given(applicationReviewLogRepository.listByApplicationId(21013L)).willReturn(List.of(new ApplicationReviewLog(
            31000L,
            21013L,
            ApplicationReviewAction.RETURN,
            1010L,
            "COUNSELOR",
            "补充材料",
            Instant.parse("2026-07-07T11:00:00Z")
    )));

    ReviewApplicationDetailView result = service.getReviewDetail(21013L);

    assertThat(result.application().applicationId()).isEqualTo(21013L);
    assertThat(result.applicant().userName()).isEqualTo("张三");
    assertThat(result.attachments()).hasSize(1);
    assertThat(result.attachments().get(0).getFileId()).isEqualTo("file-1");
    assertThat(objectMapper.writeValueAsString(result.attachments().get(0))).doesNotContain("storageKey");
    assertThat(result.reviewLogs()).hasSize(1);
    assertThat(result.reviewLogs().get(0).action()).isEqualTo("RETURN");
    assertThat(result.allowedActions()).containsExactly("APPROVE", "RETURN", "REJECT");
    verify(reviewApplicationAccessValidator).requireAccess(any(), any());
}

@Test
void shouldReturnEmptyAllowedActionsForApprovedDetail() {
    ReviewApplicationQueryRow row = submittedRow();
    row.setStatus("APPROVED");
    given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());
    given(reviewApplicationQueryRepository.findReviewApplicationDetail(any(), any())).willReturn(Optional.of(row));
    given(applicationReviewLogRepository.listByApplicationId(21013L)).willReturn(List.of());

    ReviewApplicationDetailView result = service.getReviewDetail(21013L);

    assertThat(result.application().status()).isEqualTo("APPROVED");
    assertThat(result.allowedActions()).isEmpty();
}

private UserAuthorizationContext reviewer() {
    return new UserAuthorizationContext(
            1010L,
            "A0010",
            "Counselor",
            "COUNSELOR",
            Set.of("COUNSELOR"),
            Set.of("application.review"),
            List.of()
    );
}

private ReviewApplicationQueryRow submittedRow() {
    ReviewApplicationQueryRow row = new ReviewApplicationQueryRow();
    row.setApplicationId(21013L);
    row.setApplicantUserId(1001L);
    row.setApplicantUserNo("2024305999");
    row.setApplicantUserName("张三");
    row.setOrgUnitId(2010L);
    row.setOrgUnitName("计算机学院 1 班");
    row.setOrgPath("/WHUT/CS/CLASS1");
    row.setCategoryCode("INTELLECTUAL");
    row.setItemCode("INTELLECTUAL_PAPER");
    row.setAcademicYear("2025-2026");
    row.setTerm("上学期");
    row.setTitle("论文申请");
    row.setDescription("申请说明");
    row.setStatus("SUBMITTED");
    row.setSubmittedAt(LocalDateTime.of(2026, 7, 7, 10, 0));
    row.setVersion(1L);
    row.setOptionCode("OPTION_A");
    row.setAppliedPoints(new BigDecimal("2.00"));
    row.setMaxPoints(new BigDecimal("6.00"));
    row.setEvidenceCount(1);
    row.setExceedsMaxPoints(false);
    row.setAttachments(List.of(attachment()));
    return row;
}

private edu.whut.eval.application.application.query.ReviewApplicationAttachmentRow attachment() {
    edu.whut.eval.application.application.query.ReviewApplicationAttachmentRow row =
            new edu.whut.eval.application.application.query.ReviewApplicationAttachmentRow();
    row.setFileId("file-1");
    row.setStorageKey("private/uploads/a.pdf");
    row.setOriginalFilename("a.pdf");
    row.setContentType("application/pdf");
    row.setSize(128L);
    row.setSortNo(0);
    return row;
}

}

```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ReviewApplicationQueryApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because query service and view classes do not exist.

- [ ] **Step 3: Add view records**

Use records for stable JSON property names:

Create `ReviewApplicationListItemView.java`:

```java
package edu.whut.eval.application.application.query;

import java.time.Instant;

public record ReviewApplicationListItemView(Long applicationId,
                                            Long applicantUserId,
                                            String applicantUserName,
                                            String applicantUserNo,
                                            Long orgUnitId,
                                            String orgUnitName,
                                            String categoryCode,
                                            String itemCode,
                                            String title,
                                            String status,
                                            Instant submittedAt,
                                            String currentReviewNode) {
}
```

Create `ReviewApplicationDetailView.java`:

```java
package edu.whut.eval.application.application.query;

import java.util.List;

public record ReviewApplicationDetailView(ReviewApplicationSummaryView application,
                                          ReviewApplicantView applicant,
                                          List<ApplicationAttachmentView> attachments,
                                          List<ReviewLogView> reviewLogs,
                                          List<String> allowedActions) {
}
```

Create `ReviewApplicationSummaryView.java`:

```java
package edu.whut.eval.application.application.query;

import java.time.Instant;

public record ReviewApplicationSummaryView(Long applicationId,
                                           String status,
                                           String title,
                                           String description,
                                           String categoryCode,
                                           String itemCode,
                                           String academicYear,
                                           String term,
                                           Instant submittedAt,
                                           Long version,
                                           ReviewScoringSnapshotView scoringSnapshot) {
}
```

Create `ReviewApplicantView.java`:

```java
package edu.whut.eval.application.application.query;

public record ReviewApplicantView(Long userId,
                                  String userNo,
                                  String userName,
                                  Long orgUnitId,
                                  String orgUnitName) {
}
```

Create `ReviewScoringSnapshotView.java`:

```java
package edu.whut.eval.application.application.query;

import java.math.BigDecimal;

public record ReviewScoringSnapshotView(String optionCode,
                                        BigDecimal appliedPoints,
                                        BigDecimal maxPoints,
                                        int evidenceCount,
                                        boolean exceedsMaxPoints,
                                        String warningMessage) {
}
```

Create `ReviewLogView.java`:

```java
package edu.whut.eval.application.application.query;

import java.time.Instant;

public record ReviewLogView(Long reviewLogId,
                            String action,
                            Long reviewerId,
                            String reviewerName,
                            String reviewRole,
                            String reason,
                            Instant reviewedAt) {
}
```

- [ ] **Step 4: Implement query application service**

Create `ReviewApplicationQueryApplicationService.java`:

```java
package edu.whut.eval.application.application.service;

import edu.whut.eval.application.application.query.ApplicationAttachmentView;
import edu.whut.eval.application.application.query.ReviewApplicationDetailView;
import edu.whut.eval.application.application.query.ReviewApplicationListItemView;
import edu.whut.eval.application.application.query.ReviewApplicationSummaryView;
import edu.whut.eval.application.application.query.ReviewApplicantView;
import edu.whut.eval.application.application.query.ReviewLogView;
import edu.whut.eval.application.application.query.ReviewScoringSnapshotView;
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.application.model.ApplicationReviewLog;
import edu.whut.eval.domain.application.repository.ApplicationReviewLogRepository;
import edu.whut.eval.application.application.repository.ReviewApplicationQueryRepository;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.query.ReviewApplicationPageQuery;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.application.application.query.ReviewApplicationAttachmentRow;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ReviewApplicationQueryApplicationService {

    private static final String SINGLE_REVIEW = "SINGLE_REVIEW";

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final ReviewApplicationQueryRepository reviewApplicationQueryRepository;
    private final ReviewApplicationAccessValidator reviewApplicationAccessValidator;
    private final ApplicationReviewLogRepository applicationReviewLogRepository;

    public ReviewApplicationQueryApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                                    ReviewApplicationQueryRepository reviewApplicationQueryRepository,
                                                    ReviewApplicationAccessValidator reviewApplicationAccessValidator,
                                                    ApplicationReviewLogRepository applicationReviewLogRepository) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.reviewApplicationQueryRepository = reviewApplicationQueryRepository;
        this.reviewApplicationAccessValidator = reviewApplicationAccessValidator;
        this.applicationReviewLogRepository = applicationReviewLogRepository;
    }

    public PageResult<ReviewApplicationListItemView> pageReviewApplications(ReviewApplicationPageQuery query) {
        UserAuthorizationContext reviewer = requiredReviewer();
        PageResult<ReviewApplicationQueryRow> page = reviewApplicationQueryRepository.pageReviewApplications(toAccessContext(reviewer), query);
        return new PageResult<>(page.total(), page.records().stream().map(this::toListItem).toList());
    }

    public ReviewApplicationDetailView getReviewDetail(Long applicationId) {
        UserAuthorizationContext reviewer = requiredReviewer();
        ReviewApplicationQueryRow row = reviewApplicationQueryRepository.findReviewApplicationDetail(toAccessContext(reviewer), applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在"));
        reviewApplicationAccessValidator.requireAccess(reviewer, row);
        List<ReviewLogView> logs = applicationReviewLogRepository.listByApplicationId(applicationId)
                .stream()
                .map(this::toLogView)
                .toList();
        return toDetail(row, logs);
    }

    private UserAuthorizationContext requiredReviewer() {
        UserAuthorizationContext reviewer = userAuthorizationContextAssembler.requiredAuthorizationContext();
        if (!reviewer.hasAuthority(AuthorizationPermissionCodes.APPLICATION_REVIEW)) {
            throw new AccessDeniedAppException("当前审核人无审核权限");
        }
        return reviewer;
    }

    private ApplicationAccessContext toAccessContext(UserAuthorizationContext reviewer) {
        return new ApplicationAccessContext(
                reviewer.getUserId(),
                reviewer.getUserNo(),
                reviewer.getUserName(),
                reviewer.getIdentity(),
                reviewer.getRoles(),
                reviewer.getAuthorities(),
                reviewer.getScopeRules(),
                AuthorizationPermissionCodes.APPLICATION_REVIEW
        );
    }

    private ReviewApplicationListItemView toListItem(ReviewApplicationQueryRow row) {
        return new ReviewApplicationListItemView(
                row.getApplicationId(),
                row.getApplicantUserId(),
                row.getApplicantUserName(),
                row.getApplicantUserNo(),
                row.getOrgUnitId(),
                row.getOrgUnitName(),
                row.getCategoryCode(),
                row.getItemCode(),
                row.getTitle(),
                row.getStatus(),
                toInstant(row.getSubmittedAt()),
                SINGLE_REVIEW
        );
    }

    private ReviewApplicationDetailView toDetail(ReviewApplicationQueryRow row, List<ReviewLogView> logs) {
        ReviewScoringSnapshotView scoringSnapshot = new ReviewScoringSnapshotView(
                row.getOptionCode(),
                row.getAppliedPoints(),
                row.getMaxPoints(),
                row.getEvidenceCount() == null ? 0 : row.getEvidenceCount(),
                Boolean.TRUE.equals(row.getExceedsMaxPoints()),
                row.getWarningMessage()
        );
        return new ReviewApplicationDetailView(
                new ReviewApplicationSummaryView(
                        row.getApplicationId(),
                        row.getStatus(),
                        row.getTitle(),
                        row.getDescription(),
                        row.getCategoryCode(),
                        row.getItemCode(),
                        row.getAcademicYear(),
                        row.getTerm(),
                        toInstant(row.getSubmittedAt()),
                        row.getVersion(),
                        scoringSnapshot
                ),
                new ReviewApplicantView(
                        row.getApplicantUserId(),
                        row.getApplicantUserNo(),
                        row.getApplicantUserName(),
                        row.getOrgUnitId(),
                        row.getOrgUnitName()
                ),
                row.getAttachments().stream().map(this::toAttachmentView).toList(),
                logs,
                "SUBMITTED".equals(row.getStatus()) ? List.of("APPROVE", "RETURN", "REJECT") : List.of()
        );
    }

    private ApplicationAttachmentView toAttachmentView(ReviewApplicationAttachmentRow row) {
        return new ApplicationAttachmentView(
                row.getFileId(),
                row.getOriginalFilename(),
                row.getContentType(),
                row.getSize(),
                row.getSortNo() == null ? 0 : row.getSortNo()
        );
    }

    private ReviewLogView toLogView(ApplicationReviewLog log) {
        return new ReviewLogView(
                log.getId(),
                log.getAction().name(),
                log.getReviewerId(),
                null,
                log.getReviewRole(),
                log.getReason(),
                log.getReviewedAt()
        );
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
```

- [ ] **Step 5: Run service tests**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ReviewApplicationQueryApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewActionResultView.java whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicationListItemView.java whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicationDetailView.java whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicationSummaryView.java whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewApplicantView.java whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewScoringSnapshotView.java whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ReviewLogView.java whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ReviewApplicationQueryApplicationService.java whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationQueryApplicationServiceTest.java
git commit -m "feat: map review application read views"
```

---

### Task 8: Review HTTP Controller

**Files:**
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/review/ReviewApplicationController.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/review/request/ApproveReviewRequest.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/review/request/ReturnReviewRequest.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/review/request/RejectReviewRequest.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationControllerWebMvcTest.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationControllerSecurityAnnotationTest.java`

- [ ] **Step 1: Write failing WebMvc and annotation tests**

Create `ReviewApplicationControllerWebMvcTest.java`:

```java
package edu.whut.eval.app.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.application.query.ApplicationAttachmentView;
import edu.whut.eval.application.application.query.ReviewActionResultView;
import edu.whut.eval.application.application.query.ReviewApplicationDetailView;
import edu.whut.eval.application.application.query.ReviewApplicationListItemView;
import edu.whut.eval.application.application.query.ReviewApplicationSummaryView;
import edu.whut.eval.application.application.query.ReviewApplicantView;
import edu.whut.eval.application.application.query.ReviewLogView;
import edu.whut.eval.application.application.query.ReviewScoringSnapshotView;
import edu.whut.eval.application.application.service.ReviewApplicationCommandApplicationService;
import edu.whut.eval.application.application.service.ReviewApplicationQueryApplicationService;
import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.review.ReviewApplicationController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ReviewApplicationControllerWebMvcTest {

    private ReviewApplicationQueryApplicationService queryService;
    private ReviewApplicationCommandApplicationService commandService;
    private ReviewApplicationController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        queryService = mock(ReviewApplicationQueryApplicationService.class);
        commandService = mock(ReviewApplicationCommandApplicationService.class);
        controller = new ReviewApplicationController(queryService, commandService);
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldListReviewApplications() throws Exception {
        given(queryService.pageReviewApplications(any()))
                .willReturn(new PageResult<>(1, List.of(listItem())));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/review/applications")
                        .param("academicYear", "2025-2026")
                        .param("status", "SUBMITTED")
                        .param("keyword", "论文"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records[0].applicationId").value(21013))
                .andExpect(jsonPath("$.data.records[0].currentReviewNode").value("SINGLE_REVIEW"));
    }

    @Test
    void shouldReturnReviewDetailWithoutStorageKey() throws Exception {
        given(queryService.getReviewDetail(21013L)).willReturn(detailView(ApplicationSubmissionStatus.SUBMITTED));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/review/applications/21013"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application.applicationId").value(21013))
                .andExpect(jsonPath("$.data.attachments[0].fileId").value("file-1"))
                .andExpect(jsonPath("$.data.attachments[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.data.reviewLogs[0].action").value("RETURN"))
                .andExpect(jsonPath("$.data.allowedActions[0]").value("APPROVE"));
    }

    @Test
    void shouldApproveApplication() throws Exception {
        given(commandService.approve(any())).willReturn(actionResult(ApplicationSubmissionStatus.APPROVED));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(post("/api/review/applications/21013/approve")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApprovePayload(1L, "同意"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewLogId").value(31001));
    }

    @Test
    void shouldReturn400WhenReturnReasonIsBlank() throws Exception {
        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(post("/api/review/applications/21013/return")
                        .contentType(APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"));
    }

    @Test
    void shouldRejectApplication() throws Exception {
        given(commandService.reject(any())).willReturn(actionResult(ApplicationSubmissionStatus.REJECTED));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(post("/api/review/applications/21013/reject")
                        .contentType(APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"reason\":\"不符合要求\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    private ReviewApplicationListItemView listItem() {
        return new ReviewApplicationListItemView(
                21013L,
                1001L,
                "张三",
                "2024305999",
                2010L,
                "计算机学院 1 班",
                "INTELLECTUAL",
                "INTELLECTUAL_PAPER",
                "论文申请",
                "SUBMITTED",
                Instant.parse("2026-07-07T10:00:00Z"),
                "SINGLE_REVIEW"
        );
    }

    private ReviewApplicationDetailView detailView(ApplicationSubmissionStatus status) {
        return new ReviewApplicationDetailView(
                new ReviewApplicationSummaryView(
                        21013L,
                        status.name(),
                        "论文申请",
                        "申请说明",
                        "INTELLECTUAL",
                        "INTELLECTUAL_PAPER",
                        "2025-2026",
                        "上学期",
                        Instant.parse("2026-07-07T10:00:00Z"),
                        1L,
                        new ReviewScoringSnapshotView("OPTION_A", new BigDecimal("2.00"), new BigDecimal("6.00"), 1, false, null)
                ),
                new ReviewApplicantView(1001L, "2024305999", "张三", 2010L, "计算机学院 1 班"),
                List.of(new ApplicationAttachmentView("file-1", "a.pdf", "application/pdf", 128L, 0)),
                List.of(new ReviewLogView(31000L, "RETURN", 1010L, null, "COUNSELOR", "补充材料", Instant.parse("2026-07-07T11:00:00Z"))),
                status == ApplicationSubmissionStatus.SUBMITTED ? List.of("APPROVE", "RETURN", "REJECT") : List.of()
        );
    }

    private ReviewActionResultView actionResult(ApplicationSubmissionStatus status) {
        return new ReviewActionResultView(21013L, status, 2L, 31001L, Instant.parse("2026-07-07T12:00:00Z"));
    }

    private record ApprovePayload(Long expectedVersion, String comment) {
    }
}
```

Create `ReviewApplicationControllerSecurityAnnotationTest.java`:

```java
package edu.whut.eval.app.review;

import edu.whut.eval.interfaces.review.ReviewApplicationController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewApplicationControllerSecurityAnnotationTest {

    private static final String REVIEW_AUTH =
            "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_REVIEW)";

    @Test
    void shouldRequireApplicationReviewAuthorityOnAllEndpoints() {
        Set<String> endpointMethods = Set.of("pageApplications", "getDetail", "approve", "returnForFix", "reject");
        Set<String> annotatedMethods = Arrays.stream(ReviewApplicationController.class.getDeclaredMethods())
                .filter(method -> endpointMethods.contains(method.getName()))
                .peek(method -> {
                    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
                    assertThat(preAuthorize)
                            .as(method.getName() + " must declare @PreAuthorize")
                            .isNotNull();
                    assertThat(preAuthorize.value()).isEqualTo(REVIEW_AUTH);
                })
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertThat(annotatedMethods).containsExactlyInAnyOrderElementsOf(endpointMethods);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ReviewApplicationControllerWebMvcTest,ReviewApplicationControllerSecurityAnnotationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because controller and request classes do not exist.

- [ ] **Step 3: Add request classes**

Create `ApproveReviewRequest.java`:

```java
package edu.whut.eval.interfaces.review.request;

import jakarta.validation.constraints.NotNull;

public class ApproveReviewRequest {
    @NotNull
    private Long expectedVersion;
    private String comment;

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
```

Create `ReturnReviewRequest.java`:

```java
package edu.whut.eval.interfaces.review.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ReturnReviewRequest {
    @NotNull
    private Long expectedVersion;

    @NotBlank
    private String reason;

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
```

Create `RejectReviewRequest.java`:

```java
package edu.whut.eval.interfaces.review.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RejectReviewRequest {
    @NotNull
    private Long expectedVersion;

    @NotBlank
    private String reason;

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
```

- [ ] **Step 4: Add controller**

Create `ReviewApplicationController.java`:

```java
package edu.whut.eval.interfaces.review;

import edu.whut.eval.application.application.command.ApproveReviewCommand;
import edu.whut.eval.application.application.command.RejectReviewCommand;
import edu.whut.eval.application.application.command.ReturnReviewCommand;
import edu.whut.eval.application.application.query.ReviewActionResultView;
import edu.whut.eval.application.application.query.ReviewApplicationDetailView;
import edu.whut.eval.application.application.query.ReviewApplicationListItemView;
import edu.whut.eval.application.application.service.ReviewApplicationCommandApplicationService;
import edu.whut.eval.application.application.service.ReviewApplicationQueryApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.domain.application.query.ReviewApplicationPageQuery;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.review.request.ApproveReviewRequest;
import edu.whut.eval.interfaces.review.request.RejectReviewRequest;
import edu.whut.eval.interfaces.review.request.ReturnReviewRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/review/applications")
public class ReviewApplicationController {

    private final ReviewApplicationQueryApplicationService reviewApplicationQueryApplicationService;
    private final ReviewApplicationCommandApplicationService reviewApplicationCommandApplicationService;

    public ReviewApplicationController(ReviewApplicationQueryApplicationService reviewApplicationQueryApplicationService,
                                       ReviewApplicationCommandApplicationService reviewApplicationCommandApplicationService) {
        this.reviewApplicationQueryApplicationService = reviewApplicationQueryApplicationService;
        this.reviewApplicationCommandApplicationService = reviewApplicationCommandApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_REVIEW)")
    @GetMapping
    public ApiResponse<PageResult<ReviewApplicationListItemView>> pageApplications(@RequestParam(defaultValue = "1") long pageNo,
                                                                                   @RequestParam(defaultValue = "20") long pageSize,
                                                                                   @RequestParam(required = false) String academicYear,
                                                                                   @RequestParam(required = false) String categoryCode,
                                                                                   @RequestParam(required = false) String itemCode,
                                                                                   @RequestParam(required = false) String status,
                                                                                   @RequestParam(required = false) String keyword,
                                                                                   @RequestParam(required = false) Long orgUnitId) {
        return ApiResponse.success(reviewApplicationQueryApplicationService.pageReviewApplications(
                new ReviewApplicationPageQuery(pageNo, pageSize, academicYear, categoryCode, itemCode, status, keyword, orgUnitId)
        ));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_REVIEW)")
    @GetMapping("/{applicationId}")
    public ApiResponse<ReviewApplicationDetailView> getDetail(@PathVariable Long applicationId) {
        return ApiResponse.success(reviewApplicationQueryApplicationService.getReviewDetail(applicationId));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_REVIEW)")
    @PostMapping("/{applicationId}/approve")
    public ApiResponse<ReviewActionResultView> approve(@PathVariable Long applicationId,
                                                       @Valid @RequestBody ApproveReviewRequest request) {
        return ApiResponse.success(reviewApplicationCommandApplicationService.approve(
                new ApproveReviewCommand(applicationId, request.getExpectedVersion(), request.getComment())
        ));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_REVIEW)")
    @PostMapping("/{applicationId}/return")
    public ApiResponse<ReviewActionResultView> returnForFix(@PathVariable Long applicationId,
                                                            @Valid @RequestBody ReturnReviewRequest request) {
        return ApiResponse.success(reviewApplicationCommandApplicationService.returnForFix(
                new ReturnReviewCommand(applicationId, request.getExpectedVersion(), request.getReason())
        ));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_REVIEW)")
    @PostMapping("/{applicationId}/reject")
    public ApiResponse<ReviewActionResultView> reject(@PathVariable Long applicationId,
                                                      @Valid @RequestBody RejectReviewRequest request) {
        return ApiResponse.success(reviewApplicationCommandApplicationService.reject(
                new RejectReviewCommand(applicationId, request.getExpectedVersion(), request.getReason())
        ));
    }
}
```

- [ ] **Step 5: Run controller tests**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ReviewApplicationControllerWebMvcTest,ReviewApplicationControllerSecurityAnnotationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/review whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationControllerWebMvcTest.java whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationControllerSecurityAnnotationTest.java
git commit -m "feat: expose review application endpoints"
```

---

### Task 9: Security Filter Integration

**Files:**
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationSecurityIntegrationTest.java`

- [ ] **Step 1: Write failing security integration test**

Create `ReviewApplicationSecurityIntegrationTest.java`:

```java
package edu.whut.eval.app.review;

import edu.whut.eval.application.application.query.ReviewActionResultView;
import edu.whut.eval.application.application.query.ReviewApplicationDetailView;
import edu.whut.eval.application.application.query.ReviewApplicationSummaryView;
import edu.whut.eval.application.application.query.ReviewApplicantView;
import edu.whut.eval.application.application.query.ReviewScoringSnapshotView;
import edu.whut.eval.application.application.service.ReviewApplicationCommandApplicationService;
import edu.whut.eval.application.application.service.ReviewApplicationQueryApplicationService;
import edu.whut.eval.application.auth.service.AccessSessionService;
import edu.whut.eval.application.auth.service.UserAuthorizationContextLoader;
import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContextLoadRequest;
import edu.whut.eval.infra.security.config.JwtConfigurationValidator;
import edu.whut.eval.infra.security.config.SecurityConfiguration;
import edu.whut.eval.infra.security.context.SecurityContextCurrentUserProvider;
import edu.whut.eval.infra.security.context.SecurityContextUserAuthorizationContextAssembler;
import edu.whut.eval.infra.security.jwt.JwtAuthenticationFilter;
import edu.whut.eval.infra.security.jwt.JwtClaimsParser;
import edu.whut.eval.infra.security.jwt.JwtClaimsToCurrentUserMapper;
import edu.whut.eval.infra.security.jwt.JwtTokenResolver;
import edu.whut.eval.infra.security.web.RestAccessDeniedHandler;
import edu.whut.eval.infra.security.web.RestAuthenticationEntryPoint;
import edu.whut.eval.interfaces.review.ReviewApplicationController;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReviewApplicationController.class)
@ContextConfiguration(classes = ReviewApplicationSecurityIntegrationTest.TestApplication.class)
@Import({
        ReviewApplicationController.class,
        SecurityConfiguration.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        JwtAuthenticationFilter.class,
        JwtTokenResolver.class,
        JwtClaimsParser.class,
        JwtClaimsToCurrentUserMapper.class,
        SecurityContextCurrentUserProvider.class,
        SecurityContextUserAuthorizationContextAssembler.class,
        JwtConfigurationValidator.class
})
@TestPropertySource(properties = {
        "infra.security.jwt.enabled=true",
        "infra.security.jwt.algorithm=HS256",
        "infra.security.jwt.issuer=whut-eval",
        "infra.security.jwt.audience=whut-eval-api",
        "infra.security.jwt.access-token-ttl-seconds=7200",
        "infra.security.jwt.refresh-token-ttl-seconds=604800",
        "infra.security.jwt.clock-skew-seconds=60",
        "infra.security.jwt.secret=test-jwt-secret-should-be-long-enough-1234567890",
        "infra.security.jwt.user-id-claim=uid",
        "infra.security.jwt.user-no-claim=uno",
        "infra.security.jwt.user-name-claim=uname",
        "infra.security.jwt.identity-claim=identity",
        "infra.security.jwt.roles-claim=roles",
        "infra.security.jwt.authorities-claim=authorities",
        "infra.security.jwt.token-type-claim=token_type",
        "infra.security.jwt.access-token-type=access",
        "infra.security.jwt.refresh-token-type=refresh"
})
class ReviewApplicationSecurityIntegrationTest {

    private static final String SECRET = "test-jwt-secret-should-be-long-enough-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewApplicationQueryApplicationService reviewApplicationQueryApplicationService;

    @MockBean
    private ReviewApplicationCommandApplicationService reviewApplicationCommandApplicationService;

    @MockBean
    private UserAuthorizationContextLoader userAuthorizationContextLoader;

    @MockBean
    private AccessSessionService accessSessionService;

    @BeforeEach
    void setUpSecurityContext() {
        given(userAuthorizationContextLoader.load(any(UserAuthorizationContextLoadRequest.class))).willAnswer(invocation -> {
            UserAuthorizationContextLoadRequest request = invocation.getArgument(0);
            return new UserAuthorizationContext(
                    request.getUserId(),
                    request.getUserNo(),
                    request.getUserName(),
                    request.getIdentity(),
                    request.getRoles(),
                    authoritiesFor(request.getUserId()),
                    List.of()
            );
        });
        given(reviewApplicationQueryApplicationService.getReviewDetail(21013L)).willReturn(detailView());
        given(reviewApplicationCommandApplicationService.approve(any()))
                .willReturn(new ReviewActionResultView(21013L, ApplicationSubmissionStatus.APPROVED, 2L, 31001L, Instant.now()));
    }

@Test
void shouldRejectAnonymousReviewList() throws Exception {
    mockMvc.perform(get("/api/review/applications"))
            .andExpect(status().isUnauthorized());
}

@Test
void shouldRejectUserWithoutApplicationReviewOnApprove() throws Exception {
    mockMvc.perform(post("/api/review/applications/21013/approve")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1002L, "application.submit"))
                    .contentType(APPLICATION_JSON)
                    .content("{\"expectedVersion\":1}"))
            .andExpect(status().isForbidden());
}

@Test
void shouldAllowReviewerWithApplicationReviewOnDetail() throws Exception {
    mockMvc.perform(get("/api/review/applications/21013")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1010L, "application.review")))
            .andExpect(status().isOk());
}

private Set<String> authoritiesFor(Long userId) {
    if (userId == 1010L) {
        return Set.of("application.review");
    }
    return Set.of("application.submit");
}

private String tokenWithAuthorities(Long userId, String... authorities) {
    Instant now = Instant.now();
    return Jwts.builder()
            .id("access-jti-" + UUID.randomUUID())
            .subject(String.valueOf(userId))
            .issuer("whut-eval")
            .audience().add("whut-eval-api").and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(3600)))
            .claim("uid", userId)
            .claim("uno", "A0010")
            .claim("uname", "Counselor")
            .claim("identity", "COUNSELOR")
            .claim("sid", "session-no-123")
            .claim("roles", List.of("COUNSELOR"))
            .claim("authorities", Arrays.asList(authorities))
            .claim("token_type", "access")
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
}

private ReviewApplicationDetailView detailView() {
    return new ReviewApplicationDetailView(
            new ReviewApplicationSummaryView(
                    21013L,
                    "SUBMITTED",
                    "论文申请",
                    "申请说明",
                    "INTELLECTUAL",
                    "INTELLECTUAL_PAPER",
                    "2025-2026",
                    "上学期",
                    Instant.parse("2026-07-07T10:00:00Z"),
                    1L,
                    new ReviewScoringSnapshotView("OPTION_A", new BigDecimal("2.00"), new BigDecimal("6.00"), 1, false, null)
            ),
            new ReviewApplicantView(1001L, "2024305999", "张三", 2010L, "计算机学院 1 班"),
            List.of(),
            List.of(),
            List.of("APPROVE", "RETURN", "REJECT")
    );
}

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableMethodSecurity
static class TestApplication {
}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ReviewApplicationSecurityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the review controller and services do not exist yet.

- [ ] **Step 3: Run security integration test**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ReviewApplicationSecurityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add whut-eval-app/src/test/java/edu/whut/eval/app/review/ReviewApplicationSecurityIntegrationTest.java
git commit -m "test: cover review endpoint security filters"
```

---

### Task 10: B/C Persistence Integration and Regression

**Files:**
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/student/MybatisPlusApplicationSubmissionRepositoryIntegrationTest.java`

- [ ] **Step 1: Write failing scoring snapshot preservation test**

Add this test after `shouldSaveAndReloadScoringSnapshot`:

```java
@Test
void shouldPreserveApplicationFactWhenApprovingSubmittedApplication() {
    ApplicationSubmission submitted = draft("file-1", "uploads/a.pdf")
            .submit(0L, new ApplicationScoringSnapshot("OPTION_A", new BigDecimal("2.00"), new BigDecimal("6.00"), 1, false, "warning"));
    ApplicationSubmission saved = applicationSubmissionRepository.save(submitted);

    ApplicationSubmission approved = saved.approve(saved.getVersion());
    ApplicationSubmission reloaded = applicationSubmissionRepository.save(approved);

    assertThat(reloaded.getStatus()).isEqualTo(edu.whut.eval.domain.application.model.ApplicationSubmissionStatus.APPROVED);
    assertThat(reloaded.getScoringSnapshot()).isNotNull();
    assertThat(reloaded.getScoringSnapshot().optionCode()).isEqualTo("OPTION_A");
    assertThat(reloaded.getScoringSnapshot().appliedPoints()).isEqualByComparingTo("2.00");
    assertThat(reloaded.getScoringSnapshot().maxPoints()).isEqualByComparingTo("6.00");
    assertThat(reloaded.getScoringSnapshot().warningMessage()).isEqualTo("warning");
    assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM application_fact WHERE application_id = ?",
            Integer.class,
            saved.getApplicationId()
    )).isEqualTo(1);
}
```

- [ ] **Step 2: Run test**

Run:

```bash
mvn -pl whut-eval-app -Dtest=MybatisPlusApplicationSubmissionRepositoryIntegrationTest#shouldPreserveApplicationFactWhenApprovingSubmittedApplication -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS if Task 2 preserved `scoringSnapshot`; FAIL if the transition dropped it.

- [ ] **Step 3: Run focused Minimal C regression set**

Run:

```bash
mvn -pl whut-eval-app -Dtest=TeamDeliverySqlConsistencyTest,ApplicationSubmissionStateMachineTest,ApplicationReviewLogRepositoryIntegrationTest,ReviewApplicationQueryRepositoryIntegrationTest,ReviewApplicationAccessValidatorTest,ReviewApplicationCommandApplicationServiceTest,ReviewApplicationQueryApplicationServiceTest,ReviewApplicationControllerWebMvcTest,ReviewApplicationControllerSecurityAnnotationTest,ReviewApplicationSecurityIntegrationTest,MybatisPlusApplicationSubmissionRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 4: Run existing Minimal B application tests**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ApplicationSubmissionCommandApplicationServiceTest,ApplicationSubmissionDetailApplicationServiceTest,StudentApplicationSubmissionControllerWebMvcTest,StudentApplicationWriteSecurityIntegrationTest,MybatisPlusApplicationSubmissionRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add whut-eval-app/src/test/java/edu/whut/eval/app/student/MybatisPlusApplicationSubmissionRepositoryIntegrationTest.java
git commit -m "test: preserve scoring snapshot during review"
```

---

### Task 11: Final Verification

**Files:**
- Read the Maven test output and `git status`; do not create new source files in this task.

- [ ] **Step 1: Run full app module tests**

Run:

```bash
mvn -pl whut-eval-app test
```

Expected: PASS.

- [ ] **Step 2: Run full Maven test suite if app module passes**

Run:

```bash
mvn test
```

Expected: PASS. If modules without tests fail because no specified tests are present, rerun the focused commands with `-Dsurefire.failIfNoSpecifiedTests=false` and record the exact module error.

- [ ] **Step 3: Inspect git status**

Run:

```bash
git status --short
```

Expected: empty output because Tasks 1 through 10 each committed their own changes.

- [ ] **Step 4: Inspect recent commits**

Run:

```bash
git log --oneline -5
```

Expected: recent commits include the Task 1 through Task 10 commit messages from this plan, with no extra uncommitted Minimal C work left by `git status --short`.

---

## Self-Review

### Spec Coverage

- List reviewable applications: Task 4 builds repository filters and Task 7 maps dedicated list DTO; Task 8 exposes `GET /api/review/applications`.
- Review detail C-4 top-level shape: Task 7 maps `application/applicant/attachments/reviewLogs/allowedActions`; Task 8 exposes `GET /api/review/applications/{applicationId}`.
- Approve, return, reject actions: Task 2 adds aggregate transitions; Task 6 adds command service; Task 8 exposes endpoints.
- Immutable review log per action: Task 3 adds generated-key append; Task 6 writes a log in the same transaction.
- A-group permission and scope: Task 4 reuses SQL translator for list/detail; Task 5 enforces `orgPath`-bearing single-resource validation; Tasks 8 and 9 enforce method/filter security.
- Safe SQL: Task 1 creates non-destructive SQL and H2 rerun tests.
- Scoring snapshot preservation: Task 2 preserves the field in transitions; Task 10 verifies `application_fact` survives approve.
- Storage internals hidden: Task 7 maps attachment DTOs without `storageKey`; Task 8 WebMvc tests assert `storageKey` is absent.
- Existing Minimal B behavior: Task 10 reruns B command/detail/controller/security/repository tests.

### Red Flag Scan

A final text scan found no unresolved planning markers, vague test directives, code-generation shortcuts, or angle-bracket file tokens.

### Type Consistency

- Command action names are `ApplicationReviewAction.APPROVE`, `RETURN`, and `REJECT`.
- Response status values come from `ApplicationSubmissionStatus`.
- Query repository returns `ReviewApplicationQueryRow`; application service maps it to `ReviewApplicationListItemView` and `ReviewApplicationDetailView`.
- Single-resource validation uses `ReviewApplicationQueryRow.orgPath`, not the bare aggregate.
