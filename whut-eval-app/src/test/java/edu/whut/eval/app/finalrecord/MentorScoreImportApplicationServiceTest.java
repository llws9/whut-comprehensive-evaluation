package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.finalrecord.importing.ImportMentorScoresCommand;
import edu.whut.eval.application.finalrecord.importing.MentorScoreImportApplicationService;
import edu.whut.eval.application.finalrecord.importing.MentorScoreImportParser;
import edu.whut.eval.application.finalrecord.importing.MentorScoreImportRepository;
import edu.whut.eval.application.finalrecord.importing.MentorScoreImportStudentTarget;
import edu.whut.eval.application.finalrecord.importing.MentorScoreImportedComponent;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.importing.MentorScoreImportResult;
import edu.whut.eval.domain.finalrecord.importing.MentorScoreImportRow;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MentorScoreImportApplicationServiceTest {

    private final UserAuthorizationContextAssembler authorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final MentorScoreImportParser parser = mock(MentorScoreImportParser.class);
    private final MentorScoreImportRepository repository = mock(MentorScoreImportRepository.class);
    private final MentorScoreImportApplicationService service =
            new MentorScoreImportApplicationService(authorizationContextAssembler, parser, repository);

    @Test
    void shouldRejectInvalidAcademicYear() {
        assertThatThrownBy(() -> service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025", "UPSERT")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2027", "UPSERT")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
    }

    @Test
    void shouldRejectInvalidImportMode() {
        assertThatThrownBy(() -> service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "MERGE")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("importMode 仅允许 UPSERT 或 STRICT_INSERT");
    }

    @Test
    void shouldRejectMissingScoreImportAuthority() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(context(Set.of(), List.of()));

        assertThatThrownBy(() -> service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "UPSERT")))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户无导入权限");
    }

    @Test
    void shouldReturnFirstFieldFailurePerRow() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(row(2, "", "BAD", "", "abc")));

        MentorScoreImportResult result = service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "UPSERT"));

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.successCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failedRows()).hasSize(1);
        assertThat(result.failedRows().get(0).code()).isEqualTo("STUDENT_NO_REQUIRED");
        assertThat(result.failedRows().get(0).message()).isEqualTo("studentNo 不能为空");
        assertThat(result.failedRows().get(0).rawValue()).containsEntry("categoryCode", "BAD");
        verify(repository, never()).upsertDraftComponent(any(), any());
    }

    @Test
    void shouldReturnStudentNotFoundForMissingEligibleStudent() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(row(2, "S404", "MORAL", "MORAL_HONOR", "1.00")));
        given(repository.findTarget("S404", "2025-2026")).willReturn(Optional.empty());

        MentorScoreImportResult result = service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "UPSERT"));

        assertThat(result.failedRows()).extracting("code").containsExactly("STUDENT_NOT_FOUND");
        verify(repository, never()).upsertDraftComponent(any(), any());
    }

    @Test
    void shouldReturnOutOfScopeWhenOrgPathIsOutsideRealSubtree() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(row(2, "S1001", "MORAL", "MORAL_HONOR", "1.00")));
        given(repository.findTarget("S1001", "2025-2026"))
                .willReturn(Optional.of(new MentorScoreImportStudentTarget(1001L, "S1001", 3010L, "/WHUT/ME/ME2022/ME2201", null)));
        given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));

        MentorScoreImportResult result = service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "UPSERT"));

        assertThat(result.failedRows()).extracting("code").containsExactly("OUT_OF_SCOPE");
    }

    @Test
    void shouldNotMatchSimilarOrgSubtreePrefix() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(row(2, "S1001", "MORAL", "MORAL_HONOR", "1.00")));
        given(repository.findTarget("S1001", "2025-2026"))
                .willReturn(Optional.of(new MentorScoreImportStudentTarget(1001L, "S1001", 3010L, "/WHUT/CS2/CS2201", null)));
        given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));

        MentorScoreImportResult result = service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "UPSERT"));

        assertThat(result.failedRows()).extracting("code").containsExactly("OUT_OF_SCOPE");
    }

    @Test
    void shouldReturnLockedWhenFinalRecordIsSubmitted() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(row(2, "S1001", "MORAL", "MORAL_HONOR", "1.00")));
        given(repository.findTarget("S1001", "2025-2026")).willReturn(Optional.of(target("SUBMITTED")));
        given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));

        MentorScoreImportResult result = service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "UPSERT"));

        assertThat(result.failedRows()).extracting("code").containsExactly("FINAL_RECORD_LOCKED");
        verify(repository, never()).upsertDraftComponent(any(), any());
    }

    @Test
    void shouldApplyUpsertDuplicateWorkbookTargetsInRowOrder() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(
                row(2, "S1001", "MORAL", "MORAL_HONOR", "1.00"),
                row(3, "S1001", "MORAL", "MORAL_HONOR", "2.00")));
        given(repository.findTarget("S1001", "2025-2026")).willReturn(Optional.of(target(null)));
        given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));

        MentorScoreImportResult result = service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "UPSERT"));

        assertThat(result.successCount()).isEqualTo(2);
        verify(repository).upsertDraftComponent(
                org.mockito.ArgumentMatchers.argThat(component -> component.rowNo().equals(2L)
                        && component.scoreValue().compareTo(new BigDecimal("1.00")) == 0),
                startsWith("D7-"));
        verify(repository).upsertDraftComponent(
                org.mockito.ArgumentMatchers.argThat(component -> component.rowNo().equals(3L)
                        && component.scoreValue().compareTo(new BigDecimal("2.00")) == 0),
                startsWith("D7-"));
    }

    @Test
    void shouldDefaultDisplayTextAndSourceRefIdForSuccessfulRows() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(new MentorScoreImportRow(2L, "S1001", "LABOR", "LABOR_SERVICE", "2.00", null, null)));
        given(repository.findTarget("S1001", "2025-2026")).willReturn(Optional.of(target(null)));
        given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));

        service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "UPSERT"));

        verify(repository).upsertDraftComponent(
                org.mockito.ArgumentMatchers.argThat(component -> component.displayText().equals("导师/固定成绩导入")
                        && component.sourceRefId().startsWith("D7-")
                        && component.sourceRefId().endsWith(":2")),
                startsWith("D7-"));
    }

    @Test
    void shouldRejectStrictInsertWorkbookDuplicateBeforeMutation() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(
                row(2, "S1001", "MORAL", "MORAL_HONOR", "1.00"),
                row(3, "S1001", "MORAL", "MORAL_HONOR", "2.00")));
        given(repository.findTarget("S1001", "2025-2026")).willReturn(Optional.of(target(null)));

        assertThatThrownBy(() -> service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "STRICT_INSERT")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("STRICT_INSERT 模式不允许覆盖");
        verify(repository, never()).upsertDraftComponent(any(), any());
    }

    @Test
    void shouldRejectStrictInsertDatabaseDuplicateBeforeMutation() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of(row(2, "S1001", "MORAL", "MORAL_HONOR", "1.00")));
        given(repository.findTarget("S1001", "2025-2026")).willReturn(Optional.of(target(null)));
        given(repository.importedComponentExists(1001L, "2025-2026", "MORAL", "MORAL_HONOR")).willReturn(true);

        assertThatThrownBy(() -> service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "STRICT_INSERT")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("STRICT_INSERT 模式不允许覆盖");
        verify(repository, never()).upsertDraftComponent(any(), any());
    }

    @Test
    void shouldDeclareTransactionalBoundary() throws Exception {
        assertThat(MentorScoreImportApplicationService.class
                .getMethod("importMentorScores", ImportMentorScoresCommand.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    private MentorScoreImportRow row(long rowNo, String studentNo, String categoryCode, String itemCode, String scoreValue) {
        return new MentorScoreImportRow(rowNo, studentNo, categoryCode, itemCode, scoreValue, "导师评分", "source-" + rowNo);
    }

    private UserAuthorizationContext scopedAdmin() {
        return context(Set.of("score.import"), List.of(
                new IamScopeRule(7010L, "score.import", "ORG_SUBTREE", 2002L, null, null, null, 80, "ACTIVE")
        ));
    }

    private UserAuthorizationContext context(Set<String> authorities, List<IamScopeRule> scopeRules) {
        return new UserAuthorizationContext(1010L, "T1010", "Counselor", "teacher", Set.of("COUNSELOR"), authorities, scopeRules);
    }

    private MentorScoreImportStudentTarget target(String status) {
        return new MentorScoreImportStudentTarget(1001L, "S1001", 2010L, "/WHUT/CS/CS2022/CS2201", status);
    }
}
