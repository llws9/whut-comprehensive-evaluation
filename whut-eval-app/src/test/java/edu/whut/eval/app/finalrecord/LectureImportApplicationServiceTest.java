package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.finalrecord.importing.ImportLecturesCommand;
import edu.whut.eval.application.finalrecord.importing.LectureImportApplicationService;
import edu.whut.eval.application.finalrecord.importing.LectureImportBatchLock;
import edu.whut.eval.application.finalrecord.importing.LectureImportParser;
import edu.whut.eval.application.finalrecord.importing.LectureImportRepository;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.importing.LectureImportResult;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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

    private ImportLecturesCommand command(String title, String heldAt, String academicYear) {
        return new ImportLecturesCommand(new byte[]{1}, title, heldAt, academicYear);
    }

    private UserAuthorizationContext scopedAdmin() {
        return new UserAuthorizationContext(1010L, "T1010", "Counselor", "teacher", Set.of("COUNSELOR"), Set.of("score.import"), List.of(
                new IamScopeRule(7010L, "score.import", "ORG_SUBTREE", 2002L, null, null, null, 80, "ACTIVE")
        ));
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
}
