package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportApplicationService;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportFile;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportGenerationException;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportQuery;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportRow;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportWorkbookWriter;
import edu.whut.eval.application.finalrecord.repository.FinalRecordQueryRepository;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.query.FinalRecordAccessContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(OutputCaptureExtension.class)
class FinalScoreExportApplicationServiceTest {

    private final UserAuthorizationContextAssembler authorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final FinalRecordQueryRepository repository = mock(FinalRecordQueryRepository.class);
    private final FinalScoreExportWorkbookWriter writer = mock(FinalScoreExportWorkbookWriter.class);
    private final FinalScoreExportApplicationService service = new FinalScoreExportApplicationService(
            authorizationContextAssembler,
            repository,
            writer
    );

    @Test
    void shouldRequireScoreExportAssignedPermission() {
        FinalScoreExportQuery query = query();
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContext(Set.of(
                AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED
        )));

        assertThatThrownBy(() -> service.export(query))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户无最终成绩导出权限");

        verify(repository, never()).listAdminFinalScoreExportRows(any(), any(), anyInt());
        verify(writer, never()).write(any(), any());
    }

    @Test
    void shouldUseExportPermissionScopeAndProbeLimit() {
        FinalScoreExportQuery query = query();
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContext(Set.of(
                AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED
        )));
        given(repository.listAdminFinalScoreExportRows(any(), same(query), eq(FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS + 1)))
                .willReturn(List.of(row(41001L)));
        given(writer.write(eq("2025-2026"), any())).willReturn(file());

        FinalScoreExportFile result = service.export(query);

        assertThat(result.filename()).isEqualTo("final-scores-2025-2026.xlsx");
        ArgumentCaptor<FinalRecordAccessContext> accessContextCaptor =
                ArgumentCaptor.forClass(FinalRecordAccessContext.class);
        verify(repository).listAdminFinalScoreExportRows(
                accessContextCaptor.capture(),
                same(query),
                eq(FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS + 1)
        );
        assertThat(accessContextCaptor.getValue().getPermissionCode())
                .isEqualTo(AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED);
    }

    @Test
    void shouldReturnNotFoundWhenNoRowsMatch() {
        FinalScoreExportQuery query = query();
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContext(Set.of(
                AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED
        )));
        given(repository.listAdminFinalScoreExportRows(any(), same(query), eq(FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS + 1)))
                .willReturn(List.of());

        assertThatThrownBy(() -> service.export(query))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("无匹配导出数据");

        verify(writer, never()).write(any(), any());
    }

    @Test
    void shouldFailBeforeWriterWhenRowCapExceeded(CapturedOutput output) {
        FinalScoreExportQuery query = new FinalScoreExportQuery("2025-2026", "SUBMITTED", "CS2022", List.of("CS2201,CS2202"));
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContext(Set.of(
                AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED
        )));
        given(repository.listAdminFinalScoreExportRows(any(), same(query), eq(FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS + 1)))
                .willReturn(rows(FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS + 1));

        assertThatThrownBy(() -> service.export(query))
                .isInstanceOf(FinalScoreExportGenerationException.class)
                .hasMessage("Excel 生成失败");

        verify(writer, never()).write(any(), any());
        assertThat(output).contains("final-score-export.row-cap-exceeded")
                .contains("academicYear=2025-2026")
                .contains("status=SUBMITTED")
                .contains("grade=CS2022")
                .contains("classes=[CS2201, CS2202]")
                .contains("returnedRowCount=20001")
                .contains("maxSyncExportRows=20000");
        assertThat(output).doesNotContain("final-score-export.workbook-writer-failed");
    }

    @Test
    void shouldPassExactlyMaxRowsToWriter() {
        FinalScoreExportQuery query = query();
        List<FinalScoreExportRow> rows = rows(FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS);
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContext(Set.of(
                AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED
        )));
        given(repository.listAdminFinalScoreExportRows(any(), same(query), eq(FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS + 1)))
                .willReturn(rows);
        given(writer.write(eq("2025-2026"), same(rows))).willReturn(file());

        service.export(query);

        verify(writer).write("2025-2026", rows);
    }

    @Test
    void shouldWrapRuntimeWriterFailureAndLogOriginalException(CapturedOutput output) {
        FinalScoreExportQuery query = query();
        IllegalStateException failure = new IllegalStateException("poi exploded");
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContext(Set.of(
                AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED
        )));
        given(repository.listAdminFinalScoreExportRows(any(), same(query), eq(FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS + 1)))
                .willReturn(List.of(row(41001L)));
        given(writer.write(eq("2025-2026"), any())).willThrow(failure);

        assertThatThrownBy(() -> service.export(query))
                .isInstanceOf(FinalScoreExportGenerationException.class)
                .hasMessage("Excel 生成失败")
                .hasCause(failure);

        assertThat(output).contains("final-score-export.workbook-writer-failed")
                .contains("academicYear=2025-2026")
                .contains("rowCount=1")
                .contains("IllegalStateException")
                .contains("poi exploded");
    }

    @Test
    void shouldRethrowWriterGenerationExceptionAndLogRootCause(CapturedOutput output) {
        FinalScoreExportQuery query = query();
        IllegalArgumentException cause = new IllegalArgumentException("bad workbook");
        FinalScoreExportGenerationException failure = new FinalScoreExportGenerationException("Excel 生成失败", cause);
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContext(Set.of(
                AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED
        )));
        given(repository.listAdminFinalScoreExportRows(any(), same(query), eq(FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS + 1)))
                .willReturn(List.of(row(41001L)));
        given(writer.write(eq("2025-2026"), any())).willThrow(failure);

        assertThatThrownBy(() -> service.export(query))
                .isSameAs(failure);

        assertThat(output).contains("final-score-export.workbook-writer-failed")
                .contains("IllegalArgumentException")
                .contains("bad workbook");
    }

    @Test
    void shouldQueryRepositoryForEveryExportCall() {
        FinalScoreExportQuery query = query();
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContext(Set.of(
                AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED
        )));
        given(repository.listAdminFinalScoreExportRows(any(), same(query), eq(FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS + 1)))
                .willReturn(List.of(row(41001L)));
        given(writer.write(eq("2025-2026"), any())).willReturn(file());

        service.export(query);
        service.export(query);

        verify(repository, times(2)).listAdminFinalScoreExportRows(
                any(),
                same(query),
                eq(FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS + 1)
        );
    }

    private FinalScoreExportQuery query() {
        return new FinalScoreExportQuery("2025-2026", null, null, List.of());
    }

    private UserAuthorizationContext adminContext(Set<String> authorities) {
        return new UserAuthorizationContext(1010L, "T1010", "Counselor", "teacher", Set.of("counselor"), authorities, List.of());
    }

    private FinalScoreExportFile file() {
        return new FinalScoreExportFile(
                "final-scores-2025-2026.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3}
        );
    }

    private List<FinalScoreExportRow> rows(int count) {
        List<FinalScoreExportRow> rows = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rows.add(row(41000L + index));
        }
        return rows;
    }

    private FinalScoreExportRow row(Long id) {
        return new FinalScoreExportRow(
                id,
                1001L,
                "2024305001",
                "Student",
                "CS2022",
                "2022级计算机",
                "CS2201",
                "计科一班",
                "2025-2026",
                "SUBMITTED",
                new BigDecimal("0.80"),
                new BigDecimal("2.00"),
                new BigDecimal("0.60"),
                new BigDecimal("1.20"),
                new BigDecimal("4.60"),
                Instant.parse("2026-07-07T12:00:00Z"),
                null
        );
    }
}
