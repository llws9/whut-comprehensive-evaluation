package edu.whut.eval.app.finalrecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.auth.service.DefaultAuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.DefaultResourceScopeAccessEvaluator;
import edu.whut.eval.application.auth.service.JsonScopeRuleExpressionInterpreter;
import edu.whut.eval.application.auth.service.ResourceScopeAccessEvaluator;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.finalrecord.importing.ActivityImportApplicationService;
import edu.whut.eval.application.finalrecord.importing.ActivityImportBatchLock;
import edu.whut.eval.application.finalrecord.importing.ActivityImportItemDefinition;
import edu.whut.eval.application.finalrecord.importing.ActivityImportParser;
import edu.whut.eval.application.finalrecord.importing.ActivityImportRepository;
import edu.whut.eval.application.finalrecord.importing.ActivityImportStudentTarget;
import edu.whut.eval.application.finalrecord.importing.ActivityImportedComponent;
import edu.whut.eval.application.finalrecord.importing.ImportActivitiesCommand;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.importing.ActivityImportFailedRow;
import edu.whut.eval.domain.finalrecord.importing.ActivityImportResult;
import edu.whut.eval.domain.finalrecord.importing.ActivityImportRow;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ActivityImportApplicationServiceTest {

    private final UserAuthorizationContextAssembler authorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final ResourceScopeAccessEvaluator resourceScopeAccessEvaluator = new DefaultResourceScopeAccessEvaluator(
            new DefaultAuthorizationScopeEvaluator(),
            new JsonScopeRuleExpressionInterpreter(new ObjectMapper()),
            new InMemoryOrgUnitLookupRepository()
    );
    private final ActivityImportParser parser = mock(ActivityImportParser.class);
    private final ActivityImportRepository repository = mock(ActivityImportRepository.class);
    private final RecordingLock lock = new RecordingLock();
    private final TransactionOperations transactionOperations = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    };
    private final ActivityImportApplicationService service = new ActivityImportApplicationService(
            authorizationContextAssembler,
            resourceScopeAccessEvaluator,
            parser,
            repository,
            lock,
            transactionOperations
    );

    @Test
    void shouldRejectRequestParametersWithFrozenMessagesBeforeParsing() {
        assertThatThrownBy(() -> service.importActivities(new ImportActivitiesCommand(new byte[0], "活动", "SPORTS", "1.00", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("上传文件不能为空");
        assertThatThrownBy(() -> service.importActivities(command(" ", "SPORTS", "1.00", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("title 不能为空");
        assertThatThrownBy(() -> service.importActivities(command("活动", "I".repeat(65), "1.00", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("itemCode 长度不能超过 64");
        assertThatThrownBy(() -> service.importActivities(command("活动", "SPORTS", "bad", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("scoreValue 必须是数字");
        assertThatThrownBy(() -> service.importActivities(command("活动", "SPORTS", "100000000.00", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("scoreValue 必须在 0 到 99999999.99 之间");
        assertThatThrownBy(() -> service.importActivities(command("活动", "SPORTS", "1.234", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("scoreValue 最多保留 2 位小数");
        assertThatThrownBy(() -> service.importActivities(command("活动", "SPORTS", "1.00", "2026-05-18", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("heldAt 格式非法");
        assertThatThrownBy(() -> service.importActivities(command("活动", "SPORTS", "1.00", "2026-05-18T14:30", "2025-2027")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        verify(parser, never()).parse(any());
    }

    @Test
    void shouldParseWorkbookBeforeItemLookupAndCapValidation() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willThrow(new ValidationException("导入模板错误：文件不可解析"));

        assertThatThrownBy(() -> service.importActivities(command("活动", "MISSING", "999.00", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：文件不可解析");
        verify(repository, never()).findActiveSportsItem(any());
    }

    @Test
    void shouldUseCanonicalItemAndGenerateFrozenDeterministicBatchId() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of());
        given(repository.findActiveSportsItem("SPORTS_COMPETITION"))
                .willReturn(Optional.of(new ActivityImportItemDefinition("SPORTS_COMPETITION", "SPORTS", new BigDecimal("1.00"), false)));

        ActivityImportResult result = service.importActivities(command(" 校运会志愿服务 ", "SPORTS_COMPETITION", "0.50", "2026-05-18T14:30", "2025-2026"));

        assertThat(result.activityBatchId()).isEqualTo("ACTIVITY-20252026-20260518143000-F0B289881AE3");
        assertThat(result.title()).isEqualTo("校运会志愿服务");
        assertThat(result.itemCode()).isEqualTo("SPORTS_COMPETITION");
        assertThat(result.scoreValue()).isEqualByComparingTo("0.50");
        verify(repository).activityBatchExists("2025-2026", "SPORTS", "SPORTS_COMPETITION", result.activityBatchId());
    }

    @Test
    void shouldRejectMissingItemAndItemScoreCapWithFrozenMessages() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of());
        given(repository.findActiveSportsItem("MISSING")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.importActivities(command("活动", "MISSING", "1.00", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("对应项目定义不存在");

        given(repository.findActiveSportsItem("SPORTS"))
                .willReturn(Optional.of(new ActivityImportItemDefinition("SPORTS", "SPORTS", new BigDecimal("0.50"), false)));
        assertThatThrownBy(() -> service.importActivities(command("活动", "SPORTS", "0.51", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("scoreValue 必须在 0 到项目允许范围之间");
    }

    @Test
    void shouldImportAccessibleRowsAndCollectRowFailuresSortedByRowNo() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(
                new ActivityImportRow(4L, "S4", "签到"),
                new ActivityImportRow(2L, null, "签到"),
                new ActivityImportRow(3L, "S3", "签到"),
                new ActivityImportRow(5L, "S4", "重复")
        ));
        given(repository.findActiveSportsItem("SPORTS"))
                .willReturn(Optional.of(new ActivityImportItemDefinition("SPORTS_CANON", "SPORTS", new BigDecimal("10.00"), false)));
        given(repository.findTarget("S3", "2025-2026")).willReturn(Optional.of(target(1003L, 3003L)));
        given(repository.findTarget("S4", "2025-2026")).willReturn(Optional.of(target(1004L, 2204L)));
        given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));
        given(repository.findActiveOrgPath(3003L)).willReturn(Optional.of("/WHUT/ME/ME2022/ME2201"));
        given(repository.findActiveOrgPath(2204L)).willReturn(Optional.of("/WHUT/CS/CS2022/CS2204"));
        given(repository.insertActivityComponents(eq("2025-2026"), any()))
                .willReturn(List.of(new ActivityImportFailedRow(
                        4L,
                        "FINAL_RECORD_LOCKED",
                        "已提交或已确认的最终成绩不允许导入覆盖",
                        raw("S4", "签到")
                )));

        ActivityImportResult result = service.importActivities(command("活动", "SPORTS", "1.50", "2026-05-18T14:30", "2025-2026"));

        assertThat(result.totalCount()).isEqualTo(4);
        assertThat(result.successCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(4);
        assertThat(result.failedRows()).extracting("rowNo").containsExactly(2L, 3L, 4L, 5L);
        assertThat(result.failedRows()).extracting("code").containsExactly("STUDENT_NO_REQUIRED", "OUT_OF_SCOPE", "FINAL_RECORD_LOCKED", "DUPLICATE_STUDENT");
        verify(repository).insertActivityComponents(eq("2025-2026"), org.mockito.ArgumentMatchers.argThat(components ->
                components.size() == 1
                        && components.get(0).studentUserId().equals(1004L)
                        && components.get(0).canonicalItemCode().equals("SPORTS_CANON")
                        && components.get(0).categoryCode().equals("SPORTS")
                        && components.get(0).displayText().equals("签到")
                        && components.get(0).activityBatchId().startsWith("ACTIVITY-")));
    }

    @Test
    void shouldImportRowsForAllScopeRule() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(allScopeAdmin());
        given(parser.parse(any())).willReturn(List.of(new ActivityImportRow(2L, "S1001", "校运会")));
        given(repository.findActiveSportsItem("SPORTS"))
                .willReturn(Optional.of(new ActivityImportItemDefinition("SPORTS", "SPORTS", new BigDecimal("10.00"), false)));
        given(repository.findTarget("S1001", "2025-2026")).willReturn(Optional.of(target(1001L, 9001L)));
        given(repository.insertActivityComponents(eq("2025-2026"), any())).willReturn(List.of());

        ActivityImportResult result = service.importActivities(command("活动", "SPORTS", "1.00", "2026-05-18T14:30", "2025-2026"));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        verify(repository).insertActivityComponents(eq("2025-2026"), org.mockito.ArgumentMatchers.argThat(components ->
                components.size() == 1 && components.get(0).studentUserId().equals(1001L)));
        verify(repository, never()).findActiveOrgPath(any());
    }

    @Test
    void shouldRejectOrgUnitRuleWhenItemConstraintDoesNotMatchActivityItem() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(itemScopedOrgUnitAdmin());
        given(parser.parse(any())).willReturn(List.of(new ActivityImportRow(2L, "S1001", "校运会")));
        given(repository.findActiveSportsItem("SPORTS_COMPETITION"))
                .willReturn(Optional.of(new ActivityImportItemDefinition("SPORTS_COMPETITION", "SPORTS", new BigDecimal("10.00"), false)));
        given(repository.findTarget("S1001", "2025-2026")).willReturn(Optional.of(target(1001L, 2002L)));

        ActivityImportResult result = service.importActivities(command("活动", "SPORTS_COMPETITION", "1.00", "2026-05-18T14:30", "2025-2026"));

        assertThat(result.successCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failedRows()).extracting("code").containsExactly("OUT_OF_SCOPE");
        verify(repository, never()).insertActivityComponents(any(), any());
    }

    @Test
    void shouldAllowRetryWhenAllRowsFailAndNoComponentsPersist() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(new ActivityImportRow(2L, "S404", null)));
        given(repository.findActiveSportsItem("SPORTS"))
                .willReturn(Optional.of(new ActivityImportItemDefinition("SPORTS", "SPORTS", new BigDecimal("10.00"), false)));
        given(repository.findTarget("S404", "2025-2026")).willReturn(Optional.empty());

        ActivityImportResult first = service.importActivities(command("活动", "SPORTS", "1.00", "2026-05-18T14:30", "2025-2026"));
        ActivityImportResult retry = service.importActivities(command("活动", "SPORTS", "1.00", "2026-05-18T14:30", "2025-2026"));

        assertThat(first.successCount()).isZero();
        assertThat(retry.successCount()).isZero();
        assertThat(lock.releases.get()).isEqualTo(2);
        verify(repository, never()).insertActivityComponents(any(), any());
        verify(repository, times(2)).activityBatchExists(eq("2025-2026"), eq("SPORTS"), eq("SPORTS"), eq(first.activityBatchId()));
    }

    @Test
    void shouldRejectDuplicateBatchAndUnavailableLock() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of());
        given(repository.findActiveSportsItem("SPORTS"))
                .willReturn(Optional.of(new ActivityImportItemDefinition("SPORTS", "SPORTS", new BigDecimal("10.00"), false)));
        given(repository.activityBatchExists(eq("2025-2026"), eq("SPORTS"), eq("SPORTS"), any())).willReturn(true);

        assertThatThrownBy(() -> service.importActivities(command("活动", "SPORTS", "1.00", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("同一活动批次已导入");
        assertThat(lock.releases.get()).isEqualTo(1);

        lock.available = false;
        given(repository.activityBatchExists(eq("2025-2026"), eq("SPORTS"), eq("SPORTS"), any())).willReturn(false);
        assertThatThrownBy(() -> service.importActivities(command("活动", "SPORTS", "1.00", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("同一活动批次正在导入，请稍后重试");
    }

    @Test
    void shouldRejectMissingAuthority() {
        given(authorizationContextAssembler.requiredAuthorizationContext())
                .willReturn(new UserAuthorizationContext(1L, "admin", "Admin", "teacher", Set.of(), Set.of(), List.of()));

        assertThatThrownBy(() -> service.importActivities(command("活动", "SPORTS", "1.00", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户无导入权限");
    }

    @Test
    void shouldDeferAndSwallowAfterCompletionReleaseFailure() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of());
        given(repository.findActiveSportsItem("SPORTS"))
                .willReturn(Optional.of(new ActivityImportItemDefinition("SPORTS", "SPORTS", new BigDecimal("10.00"), false)));
        lock.throwOnRelease = true;

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.importActivities(command("活动", "SPORTS", "1.00", "2026-05-18T14:30", "2025-2026"));

            assertThat(lock.releases.get()).isZero();
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            assertThat(lock.releases.get()).isEqualTo(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            lock.throwOnRelease = false;
        }
    }

    @Test
    void shouldPropagateTryAcquireDataAccessException() {
        lock.throwOnAcquire = true;
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of());
        given(repository.findActiveSportsItem("SPORTS"))
                .willReturn(Optional.of(new ActivityImportItemDefinition("SPORTS", "SPORTS", new BigDecimal("10.00"), false)));

        assertThatThrownBy(() -> service.importActivities(command("活动", "SPORTS", "1.00", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasMessage("GET_LOCK failed");
    }

    private ImportActivitiesCommand command(String title, String itemCode, String scoreValue, String heldAt, String academicYear) {
        return new ImportActivitiesCommand(new byte[]{1}, title, itemCode, scoreValue, heldAt, academicYear);
    }

    private UserAuthorizationContext scopedAdmin() {
        return new UserAuthorizationContext(1010L, "T1010", "Counselor", "teacher", Set.of("COUNSELOR"), Set.of("score.import"), List.of(
                new IamScopeRule(7010L, "score.import", "ORG_SUBTREE", 2002L, null, null, null, 80, "ACTIVE")
        ));
    }

    private UserAuthorizationContext allScopeAdmin() {
        return new UserAuthorizationContext(1010L, "T1010", "Admin", "teacher", Set.of("ADMIN"), Set.of("score.import"), List.of(
                new IamScopeRule(7011L, "score.import", "ALL", null, null, null, null, 100, "ACTIVE")
        ));
    }

    private UserAuthorizationContext itemScopedOrgUnitAdmin() {
        return new UserAuthorizationContext(1010L, "T1010", "Counselor", "teacher", Set.of("COUNSELOR"), Set.of("score.import"), List.of(
                new IamScopeRule(7012L, "score.import", "ORG_UNIT", 2002L, "SPORTS", "SPORTS_OTHER", null, 80, "ACTIVE")
        ));
    }

    private ActivityImportStudentTarget target(Long studentUserId, Long orgUnitId) {
        return new ActivityImportStudentTarget(studentUserId, "S" + studentUserId, orgUnitId);
    }

    private Map<String, String> raw(String studentNo, String displayText) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("studentNo", studentNo);
        raw.put("displayText", displayText);
        return raw;
    }

    private static class InMemoryOrgUnitLookupRepository implements OrgUnitLookupRepository {
        private final Map<Long, OrgUnit> units = Map.of(
                2002L, new OrgUnit(2002L, 2001L, "COLLEGE", "CS", "计算机与人工智能学院", "/WHUT/CS", "ACTIVE")
        );

        @Override
        public Optional<OrgUnit> findById(Long id) {
            return Optional.ofNullable(units.get(id));
        }
    }

    private static final class RecordingLock implements ActivityImportBatchLock {
        private boolean available = true;
        private boolean throwOnAcquire;
        private boolean throwOnRelease;
        private final AtomicInteger releases = new AtomicInteger();
        private final AtomicBoolean releasing = new AtomicBoolean();

        @Override
        public boolean tryAcquire(String activityBatchId, Duration timeout) {
            if (throwOnAcquire) {
                throw new DataAccessResourceFailureException("GET_LOCK failed");
            }
            return available;
        }

        @Override
        public void release(String activityBatchId) {
            releases.incrementAndGet();
            releasing.set(true);
            if (throwOnRelease) {
                throw new DataAccessResourceFailureException("RELEASE_LOCK failed");
            }
        }
    }
}
