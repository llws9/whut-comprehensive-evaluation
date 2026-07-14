package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.finalrecord.importing.ImportLecturesCommand;
import edu.whut.eval.application.finalrecord.importing.LectureImportApplicationService;
import edu.whut.eval.application.finalrecord.importing.LectureImportBatchLock;
import edu.whut.eval.application.finalrecord.importing.LectureImportParser;
import edu.whut.eval.application.finalrecord.importing.LectureImportRepository;
import edu.whut.eval.application.finalrecord.importing.LectureImportStudentTarget;
import edu.whut.eval.domain.finalrecord.importing.LectureImportFailedRow;
import edu.whut.eval.domain.finalrecord.importing.LectureImportRow;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.importing.LectureImportResult;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import org.junit.jupiter.api.Test;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

class LectureImportApplicationServiceTest {

    private final UserAuthorizationContextAssembler authorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final LectureImportParser parser = mock(LectureImportParser.class);
    private final LectureImportRepository repository = mock(LectureImportRepository.class);
    private final RecordingLock lock = new RecordingLock();
    private final TransactionOperations transactionOperations = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    };
    private final LectureImportApplicationService service =
            new LectureImportApplicationService(authorizationContextAssembler, parser, repository, lock, transactionOperations);

    @Test
    void shouldRejectInvalidRequestParametersBeforeParsing() {
        assertThatThrownBy(() -> service.importLectures(new ImportLecturesCommand(new byte[0], "讲座", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("上传文件不能为空");
        assertThatThrownBy(() -> service.importLectures(command(" ", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("title 不能为空");
        assertThatThrownBy(() -> service.importLectures(command("讲座", "bad", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("heldAt 格式非法");
        assertThatThrownBy(() -> service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2027")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2024")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> service.importLectures(command("讲座", "2026-05-18T14:30", "9999-0000")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> service.importLectures(command("讲座", "2026-05-18T14:30Z", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("heldAt 格式非法");
    }

    @Test
    void shouldGenerateDeterministicBatchIdAndNormalizedMetadata() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of());

        LectureImportResult withoutSeconds = service.importLectures(command("学院学术讲座", "2026-05-18T14:30", "2025-2026"));
        LectureImportResult result = service.importLectures(command(" 学院学术讲座 ", "2026-05-18T14:30:00.123", " 2025-2026 "));

        assertThat(withoutSeconds.heldAt().toString()).isEqualTo("2026-05-18T14:30");
        assertThat(result.lectureBatchId()).startsWith("LECTURE-20252026-20260518143000-");
        assertThat(result.lectureBatchId()).hasSize(44);
        assertThat(result.lectureBatchId()).isEqualTo(withoutSeconds.lectureBatchId());
        assertThat(result.title()).isEqualTo("学院学术讲座");
        assertThat(result.heldAt().toString()).isEqualTo("2026-05-18T14:30");
        assertThat(result.academicYear()).isEqualTo("2025-2026");
    }

    @Test
    void shouldAllowRetryWhenHeaderOnlyImportLeavesNoBatchMarker() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of());

        LectureImportResult first = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));
        LectureImportResult retry = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

        assertThat(first.totalCount()).isZero();
        assertThat(first.successCount()).isZero();
        assertThat(first.failedCount()).isZero();
        assertThat(retry.totalCount()).isZero();
        assertThat(lock.releases.get()).isEqualTo(2);
        verify(repository, never()).insertLectureComponents(any(), any(), any());
        verify(repository, times(2)).lectureBatchExists(eq("2025-2026"), eq(first.lectureBatchId()));
    }

    @Test
    void shouldRejectMissingScoreImportAuthority() {
        given(authorizationContextAssembler.requiredAuthorizationContext())
                .willReturn(new UserAuthorizationContext(1L, "admin", "Admin", "teacher", Set.of(), Set.of(), List.of()));

        assertThatThrownBy(() -> service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户无导入权限");
    }

    @Test
    void shouldRejectWhenBatchLockCannotBeAcquired() {
        lock.available = false;
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of());

        assertThatThrownBy(() -> service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("同一讲座批次正在导入，请稍后重试");
        assertThat(lock.releases.get()).isZero();
    }

    @Test
    void shouldCollectFieldFailuresInFrozenOrderAndRawValueShape() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(
                new LectureImportRow(2L, null, null, "x"),
                new LectureImportRow(3L, "S0", null, "x"),
                new LectureImportRow(4L, "S1", "99999999.999", "x"),
                new LectureImportRow(5L, "S2", "1.234", "x"),
                new LectureImportRow(6L, "S3", "1.230", "x"),
                new LectureImportRow(7L, "S4", "1.00", "一".repeat(1001))
        ));

        LectureImportResult result = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

        assertThat(result.failedRows()).extracting("code")
                .containsExactly("STUDENT_NO_REQUIRED", "SCORE_VALUE_REQUIRED", "SCORE_VALUE_OUT_OF_RANGE", "SCORE_VALUE_SCALE_INVALID", "SCORE_VALUE_SCALE_INVALID", "DISPLAY_TEXT_TOO_LONG");
        assertThat(result.failedRows().get(0).rawValue()).containsOnlyKeys("studentNo", "scoreValue", "displayText");
    }

    @Test
    void shouldValidateTitleLengthCodePointBoundaries() {
        String validTitle = "一".repeat(255);
        String tooLongTitle = "一".repeat(256);
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(new LectureImportRow(2L, "S1", "1.00", "讲座")));
        given(repository.findTarget(eq("S1"), eq("2025-2026"))).willReturn(Optional.of(target(1001L, "/WHUT/CS/CS2022/CS2201")));
        given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));
        given(repository.insertLectureComponents(eq("2025-2026"), any(), any())).willReturn(List.of());

        LectureImportResult result = service.importLectures(command(validTitle, "2026-05-18T14:30", "2025-2026"));

        assertThat(result.successCount()).isEqualTo(1);
        assertThatThrownBy(() -> service.importLectures(command(tooLongTitle, "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("title 长度不能超过 255");
    }

    @Test
    void shouldTreatZeroUpperBoundAndShortScaleScoresAsValidAndDefaultDisplayText() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(
                new LectureImportRow(2L, "S1", "0", null),
                new LectureImportRow(3L, "S2", "0.0", ""),
                new LectureImportRow(4L, "S3", "1", "一".repeat(1000)),
                new LectureImportRow(5L, "S4", "1.5", "讲座"),
                new LectureImportRow(6L, "S5", "99999999.99", ""),
                new LectureImportRow(7L, "S6", "00", ""),
                new LectureImportRow(8L, "S7", "00.50", "")
        ));
        given(repository.findTarget(eq("S1"), eq("2025-2026"))).willReturn(Optional.of(target(1001L, "/WHUT/CS/CS2022/CS2201")));
        given(repository.findTarget(eq("S2"), eq("2025-2026"))).willReturn(Optional.of(target(1002L, "/WHUT/CS/CS2022/CS2202")));
        given(repository.findTarget(eq("S3"), eq("2025-2026"))).willReturn(Optional.of(target(1003L, "/WHUT/CS/CS2022/CS2203")));
        given(repository.findTarget(eq("S4"), eq("2025-2026"))).willReturn(Optional.of(target(1004L, "/WHUT/CS/CS2022/CS2204")));
        given(repository.findTarget(eq("S5"), eq("2025-2026"))).willReturn(Optional.of(target(1005L, "/WHUT/CS/CS2022/CS2205")));
        given(repository.findTarget(eq("S6"), eq("2025-2026"))).willReturn(Optional.of(target(1006L, "/WHUT/CS/CS2022/CS2206")));
        given(repository.findTarget(eq("S7"), eq("2025-2026"))).willReturn(Optional.of(target(1007L, "/WHUT/CS/CS2022/CS2207")));
        given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));
        given(repository.insertLectureComponents(eq("2025-2026"), any(), any())).willReturn(List.of());

        LectureImportResult result = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

        assertThat(result.successCount()).isEqualTo(7);
        verify(repository).insertLectureComponents(eq("2025-2026"), any(), org.mockito.ArgumentMatchers.argThat(components ->
                components.size() == 7
                        && components.get(0).scoreValue().compareTo(new BigDecimal("0.00")) == 0
                        && components.get(1).scoreValue().compareTo(new BigDecimal("0.00")) == 0
                        && components.get(2).scoreValue().compareTo(new BigDecimal("1.00")) == 0
                        && components.get(3).scoreValue().compareTo(new BigDecimal("1.50")) == 0
                        && components.get(5).scoreValue().compareTo(new BigDecimal("0.00")) == 0
                        && components.get(6).scoreValue().compareTo(new BigDecimal("0.50")) == 0
                        && components.get(0).displayText().equals("讲座 讲座签到")
                        && components.get(1).displayText().equals("讲座 讲座签到")
                        && components.get(2).displayText().equals("一".repeat(1000))
                        && components.get(4).displayText().equals("讲座 讲座签到")));
    }

    @Test
    void shouldRejectNonStrictDecimalFormats() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(
                new LectureImportRow(2L, "S1", "-1", null),
                new LectureImportRow(3L, "S2", "1,234.56", null),
                new LectureImportRow(4L, "S3", "50%", null),
                new LectureImportRow(5L, "S4", "5E-1", null)
        ));

        LectureImportResult result = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

        assertThat(result.successCount()).isZero();
        assertThat(result.failedRows()).extracting("code")
                .containsExactly("SCORE_VALUE_INVALID", "SCORE_VALUE_INVALID", "SCORE_VALUE_INVALID", "SCORE_VALUE_INVALID");
    }

    @Test
    void shouldCollectDuplicateStudentAfterFieldValidation() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(
                new LectureImportRow(2L, "S1", "bad", null),
                new LectureImportRow(3L, "S1", "1.00", null),
                new LectureImportRow(4L, "S1", "2.00", null)
        ));
        given(repository.findTarget(eq("S1"), eq("2025-2026"))).willReturn(Optional.of(target(1001L, "/WHUT/CS/CS2022/CS2201")));
        given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));
        given(repository.insertLectureComponents(eq("2025-2026"), any(), any())).willReturn(List.of());

        LectureImportResult result = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

        assertThat(result.failedRows()).extracting("rowNo").containsExactly(2L, 4L);
        assertThat(result.failedRows()).extracting("code").containsExactly("SCORE_VALUE_INVALID", "DUPLICATE_STUDENT");
        assertThat(result.failedRows()).extracting("message").containsExactly("scoreValue 必须是数字", "同一讲座批次中学生重复");
        assertThat(result.successCount()).isEqualTo(1);
    }

    @Test
    void shouldConsumeDuplicateKeyAfterStudentLookupFailure() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(
                new LectureImportRow(2L, "S404", "1.00", null),
                new LectureImportRow(3L, "S404", "2.00", null)
        ));
        given(repository.findTarget(eq("S404"), eq("2025-2026"))).willReturn(Optional.empty());

        LectureImportResult result = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

        assertThat(result.failedRows()).extracting("rowNo").containsExactly(2L, 3L);
        assertThat(result.failedRows()).extracting("code").containsExactly("STUDENT_NOT_FOUND", "DUPLICATE_STUDENT");
    }

    @Test
    void shouldAllowRetryWhenAllRowsFailBeforePersistenceLeavesNoBatchMarker() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(
                new LectureImportRow(2L, "S404", "1.00", null)
        ));
        given(repository.findTarget(eq("S404"), eq("2025-2026"))).willReturn(Optional.empty());

        LectureImportResult first = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));
        LectureImportResult retry = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

        assertThat(first.successCount()).isZero();
        assertThat(first.failedRows()).extracting("code").containsExactly("STUDENT_NOT_FOUND");
        assertThat(retry.successCount()).isZero();
        assertThat(lock.releases.get()).isEqualTo(2);
        verify(repository, never()).insertLectureComponents(any(), any(), any());
        verify(repository, times(2)).lectureBatchExists(eq("2025-2026"), eq(first.lectureBatchId()));
    }

    @Test
    void shouldCollectStudentScopeAndLockFailuresAndSortFailedRowsByRowNo() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(
                new LectureImportRow(4L, "S4", "1.00", null),
                new LectureImportRow(2L, "S2", "1.00", null),
                new LectureImportRow(3L, "S3", "1.00", null)
        ));
        given(repository.findTarget(eq("S2"), eq("2025-2026"))).willReturn(Optional.empty());
        given(repository.findTarget(eq("S3"), eq("2025-2026"))).willReturn(Optional.of(target(1003L, "/WHUT/ME/ME2022/ME2201")));
        given(repository.findTarget(eq("S4"), eq("2025-2026"))).willReturn(Optional.of(target(1004L, "/WHUT/CS/CS2022/CS2204")));
        given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));
        given(repository.insertLectureComponents(eq("2025-2026"), any(), org.mockito.ArgumentMatchers.argThat(components -> components.size() == 1)))
                .willReturn(List.of(new LectureImportFailedRow(
                        4L,
                        "FINAL_RECORD_LOCKED",
                        "已提交或已确认的最终成绩不允许导入覆盖",
                        raw("S4", "1.00", null)
                )));

        LectureImportResult result = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

        assertThat(result.failedRows()).extracting("rowNo").containsExactly(2L, 3L, 4L);
        assertThat(result.failedRows()).extracting("code").containsExactly("STUDENT_NOT_FOUND", "OUT_OF_SCOPE", "FINAL_RECORD_LOCKED");
    }

    @Test
    void shouldApplyOrgUnitScopeAsExactMatchOnly() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(new UserAuthorizationContext(
                1010L,
                "T1010",
                "Counselor",
                "teacher",
                Set.of("COUNSELOR"),
                Set.of("score.import"),
                List.of(new IamScopeRule(7011L, "score.import", "ORG_UNIT", 2201L, null, null, null, 80, "ACTIVE"))
        ));
        given(parser.parse(any())).willReturn(List.of(
                new LectureImportRow(2L, "S1", "1.00", null),
                new LectureImportRow(3L, "S2", "1.00", null)
        ));
        given(repository.findTarget(eq("S1"), eq("2025-2026"))).willReturn(Optional.of(new LectureImportStudentTarget(1001L, "S1", 2201L, "/WHUT/CS/CS2022/CS2201")));
        given(repository.findTarget(eq("S2"), eq("2025-2026"))).willReturn(Optional.of(new LectureImportStudentTarget(1002L, "S2", 2202L, "/WHUT/CS/CS2022/CS2202")));
        given(repository.insertLectureComponents(eq("2025-2026"), any(), org.mockito.ArgumentMatchers.argThat(components -> components.size() == 1 && components.get(0).studentNo().equals("S1"))))
                .willReturn(List.of());

        LectureImportResult result = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedRows()).extracting("rowNo").containsExactly(3L);
        assertThat(result.failedRows()).extracting("code").containsExactly("OUT_OF_SCOPE");
    }

    @Test
    void shouldReturnOutOfScopeWhenOnlyUnsupportedScopeRulesExist() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(new UserAuthorizationContext(
                1010L,
                "T1010",
                "Counselor",
                "teacher",
                Set.of("COUNSELOR"),
                Set.of("score.import"),
                List.of(new IamScopeRule(7012L, "score.import", "MAJOR", 2201L, null, null, null, 80, "ACTIVE"))
        ));
        given(parser.parse(any())).willReturn(List.of(new LectureImportRow(2L, "S1", "1.00", null)));
        given(repository.findTarget(eq("S1"), eq("2025-2026")))
                .willReturn(Optional.of(new LectureImportStudentTarget(1001L, "S1", 2201L, "/WHUT/CS/CS2022/CS2201")));

        LectureImportResult result = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

        assertThat(result.successCount()).isZero();
        assertThat(result.failedRows()).extracting("code").containsExactly("OUT_OF_SCOPE");
        verify(repository, never()).insertLectureComponents(any(), any(), any());
    }

    @Test
    void shouldAllowAllScopeWithoutOrgPathLookup() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(new UserAuthorizationContext(
                1010L,
                "T1010",
                "Counselor",
                "teacher",
                Set.of("COUNSELOR"),
                Set.of("score.import"),
                List.of(new IamScopeRule(7013L, "score.import", "ALL", null, null, null, null, 80, "ACTIVE"))
        ));
        given(parser.parse(any())).willReturn(List.of(new LectureImportRow(2L, "S1", "1.00", null)));
        given(repository.findTarget(eq("S1"), eq("2025-2026")))
                .willReturn(Optional.of(new LectureImportStudentTarget(1001L, "S1", 2201L, "/WHUT/CS/CS2022/CS2201")));
        given(repository.insertLectureComponents(eq("2025-2026"), any(), org.mockito.ArgumentMatchers.argThat(components ->
                components.size() == 1 && components.get(0).studentNo().equals("S1"))))
                .willReturn(List.of());

        LectureImportResult result = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        verify(repository, never()).findActiveOrgPath(any());
    }

    @Test
    void shouldApplyUnionSemanticsAcrossMultipleScopeRules() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(new UserAuthorizationContext(
                1010L,
                "T1010",
                "Counselor",
                "teacher",
                Set.of("COUNSELOR"),
                Set.of("score.import"),
                List.of(
                        new IamScopeRule(7014L, "score.import", "MAJOR", 9999L, null, null, null, 80, "ACTIVE"),
                        new IamScopeRule(7015L, "score.import", "ORG_UNIT", 2202L, null, null, null, 80, "ACTIVE"),
                        new IamScopeRule(7016L, "score.import", "ORG_SUBTREE", 2002L, null, null, null, 80, "ACTIVE")
                )
        ));
        given(parser.parse(any())).willReturn(List.of(new LectureImportRow(2L, "S1", "1.00", null)));
        given(repository.findTarget(eq("S1"), eq("2025-2026")))
                .willReturn(Optional.of(new LectureImportStudentTarget(1001L, "S1", 2201L, "/WHUT/CS/CS2022/CS2201")));
        given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));
        given(repository.insertLectureComponents(eq("2025-2026"), any(), org.mockito.ArgumentMatchers.argThat(components ->
                components.size() == 1 && components.get(0).studentNo().equals("S1"))))
                .willReturn(List.of());

        LectureImportResult result = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
    }

    @Test
    void shouldReleaseAcquiredLockOnDuplicateConflict() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of());
        given(repository.lectureBatchExists(eq("2025-2026"), any())).willReturn(true);

        assertThatThrownBy(() -> service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("同一讲座批次已导入");
        assertThat(lock.releases.get()).isEqualTo(1);
    }

    @Test
    void shouldReleaseAcquiredLockOnPersistenceFailure() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(new LectureImportRow(2L, "S1", "1.00", null)));
        given(repository.findTarget(eq("S1"), eq("2025-2026"))).willReturn(Optional.of(target(1001L, "/WHUT/CS/CS2022/CS2201")));
        given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));
        doThrow(new ConflictException("最终成绩状态已变更，请刷新后重试"))
                .when(repository).insertLectureComponents(eq("2025-2026"), any(), any());

        assertThatThrownBy(() -> service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("最终成绩状态已变更，请刷新后重试");
        assertThat(lock.releases.get()).isEqualTo(1);
    }

    @Test
    void shouldRejectConcurrentSameBatchImportBeforeSecondMutation() throws Exception {
        UserAuthorizationContextAssembler concurrentAuthorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
        LectureImportParser concurrentParser = mock(LectureImportParser.class);
        LectureImportRepository concurrentRepository = mock(LectureImportRepository.class);
        ConcurrentLock concurrentLock = new ConcurrentLock();
        LectureImportApplicationService concurrentService = new LectureImportApplicationService(
                concurrentAuthorizationContextAssembler,
                concurrentParser,
                concurrentRepository,
                concurrentLock,
                transactionOperations
        );
        CountDownLatch firstReachedBatchCheck = new CountDownLatch(1);
        CountDownLatch allowFirstToContinue = new CountDownLatch(1);
        AtomicInteger mutations = new AtomicInteger();
        AtomicBoolean firstBatchCheck = new AtomicBoolean(true);
        given(concurrentAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(concurrentParser.parse(any())).willReturn(List.of(new LectureImportRow(2L, "S1", "1.00", null)));
        given(concurrentRepository.lectureBatchExists(eq("2025-2026"), any())).willAnswer(invocation -> {
            if (firstBatchCheck.getAndSet(false)) {
                firstReachedBatchCheck.countDown();
                assertThat(allowFirstToContinue.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return false;
        });
        given(concurrentRepository.findTarget(eq("S1"), eq("2025-2026")))
                .willReturn(Optional.of(target(1001L, "/WHUT/CS/CS2022/CS2201")));
        given(concurrentRepository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));
        given(concurrentRepository.insertLectureComponents(eq("2025-2026"), any(), any())).willAnswer(invocation -> {
            mutations.incrementAndGet();
            return List.of();
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<LectureImportResult> first = CompletableFuture.supplyAsync(
                    () -> concurrentService.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026")),
                    executor
            );
            assertThat(firstReachedBatchCheck.await(5, TimeUnit.SECONDS)).isTrue();

            CompletableFuture<Throwable> second = CompletableFuture.supplyAsync(() -> {
                try {
                    concurrentService.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));
                    return null;
                } catch (Throwable throwable) {
                    return throwable;
                }
            }, executor);

            assertThat(second.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("同一讲座批次正在导入，请稍后重试");
            allowFirstToContinue.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).successCount()).isEqualTo(1);
            assertThat(mutations.get()).isEqualTo(1);
            verify(concurrentRepository, times(1)).insertLectureComponents(eq("2025-2026"), any(), any());
            verify(concurrentRepository, times(1)).lectureBatchExists(eq("2025-2026"), any());
        } finally {
            allowFirstToContinue.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldDeferLockReleaseUntilTransactionCompletionWhenSynchronizationIsActive() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of());

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

            assertThat(lock.releases.get()).isZero();
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            assertThat(lock.releases.get()).isEqualTo(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private ImportLecturesCommand command(String title, String heldAt, String academicYear) {
        return new ImportLecturesCommand(new byte[]{1}, title, heldAt, academicYear);
    }

    private UserAuthorizationContext scopedAdmin() {
        return new UserAuthorizationContext(1010L, "T1010", "Counselor", "teacher", Set.of("COUNSELOR"), Set.of("score.import"), List.of(
                new IamScopeRule(7010L, "score.import", "ORG_SUBTREE", 2002L, null, null, null, 80, "ACTIVE")
        ));
    }

    private LectureImportStudentTarget target(Long studentUserId, String orgPath) {
        return new LectureImportStudentTarget(studentUserId, "S" + studentUserId, 2010L, orgPath);
    }

    private Map<String, String> raw(String studentNo, String scoreValue, String displayText) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("studentNo", studentNo);
        raw.put("scoreValue", scoreValue);
        raw.put("displayText", displayText);
        return raw;
    }

    private static class RecordingLock implements LectureImportBatchLock {
        private boolean available = true;
        private final AtomicInteger releases = new AtomicInteger();

        @Override
        public boolean tryAcquire(String lectureBatchId, Duration timeout) {
            return available;
        }

        @Override
        public void release(String lectureBatchId) {
            releases.incrementAndGet();
        }
    }

    private static class ConcurrentLock implements LectureImportBatchLock {
        private final Set<String> held = java.util.concurrent.ConcurrentHashMap.newKeySet();

        @Override
        public boolean tryAcquire(String lectureBatchId, Duration timeout) {
            return held.add(lectureBatchId);
        }

        @Override
        public void release(String lectureBatchId) {
            held.remove(lectureBatchId);
        }
    }
}
