package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.finalrecord.importing.ActivityImportedComponent;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.infra.persistence.dataobject.FinalRecordDO;
import edu.whut.eval.infra.persistence.mapper.ActivityImportMapper;
import edu.whut.eval.infra.persistence.repository.MybatisActivityImportRepository;
import edu.whut.eval.infra.persistence.repository.row.ActivityImportedComponentRow;
import edu.whut.eval.infra.persistence.repository.row.ActivityScoreCategoryTotalRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MybatisActivityImportRepositoryTest {

    @Mock
    private ActivityImportMapper mapper;

    @InjectMocks
    private MybatisActivityImportRepository repository;

    @Test
    void shouldReloadInsertedDraftForUpdateBeforeWritingActivityComponent() {
        ActivityImportedComponent component = component(2L, 1001L, "S1001", "SPORTS_COMPETITION", "SPORTS", "1.25", "校运会");
        FinalRecordDO lockedDraft = draftRecord(99001L, 1001L, "2025-2026");

        given(mapper.selectFinalRecordForUpdate(1001L, "2025-2026"))
                .willReturn(null, lockedDraft);
        given(mapper.insertDraft(any(FinalRecordDO.class)))
                .willAnswer(invocation -> {
                    FinalRecordDO inserted = invocation.getArgument(0);
                    inserted.setId(11001L);
                    return 1;
                });
        given(mapper.selectTotals(99001L))
                .willReturn(List.of(total("SPORTS", "1.25")));
        given(mapper.updateTotals(eq(99001L), any(), any(), any(), any(), any(), any(LocalDateTime.class)))
                .willReturn(1);

        repository.insertActivityComponents("2025-2026", List.of(component));

        ArgumentCaptor<ActivityImportedComponentRow> componentCaptor =
                ArgumentCaptor.forClass(ActivityImportedComponentRow.class);
        verify(mapper).insertActivityComponent(componentCaptor.capture());
        assertThat(componentCaptor.getValue().getFinalRecordId()).isEqualTo(99001L);
        assertThat(componentCaptor.getValue().getCategoryCode()).isEqualTo("SPORTS");
        assertThat(componentCaptor.getValue().getItemCode()).isEqualTo("SPORTS_COMPETITION");
        assertThat(componentCaptor.getValue().getSourceRefId()).isEqualTo("ACTIVITY-20252026-20260518143000-BATCH000001");
        verify(mapper).selectTotals(99001L);
        verify(mapper).updateTotals(eq(99001L), any(), any(), any(), any(), any(), any(LocalDateTime.class));
    }

    @Test
    void shouldUseOneAuditTimestampForCreatedDraftComponentAndTotalsInSingleRequest() {
        ActivityImportedComponent component = component(2L, 1001L, "S1001", "SPORTS_COMPETITION", "SPORTS", "1.25", "校运会");

        given(mapper.selectFinalRecordForUpdate(1001L, "2025-2026"))
                .willReturn(null)
                .willAnswer(invocation -> draftRecord(99001L, 1001L, "2025-2026"));
        given(mapper.insertDraft(any(FinalRecordDO.class))).willReturn(1);
        given(mapper.selectTotals(99001L))
                .willReturn(List.of(total("SPORTS", "1.25")));
        given(mapper.updateTotals(eq(99001L), any(), any(), any(), any(), any(), any(LocalDateTime.class)))
                .willReturn(1);

        repository.insertActivityComponents("2025-2026", List.of(component));

        ArgumentCaptor<FinalRecordDO> draftCaptor = ArgumentCaptor.forClass(FinalRecordDO.class);
        ArgumentCaptor<ActivityImportedComponentRow> componentCaptor =
                ArgumentCaptor.forClass(ActivityImportedComponentRow.class);
        ArgumentCaptor<LocalDateTime> totalsUpdatedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).insertDraft(draftCaptor.capture());
        verify(mapper).insertActivityComponent(componentCaptor.capture());
        verify(mapper).updateTotals(
                eq(99001L),
                any(),
                any(),
                any(),
                any(),
                any(),
                totalsUpdatedAtCaptor.capture()
        );

        LocalDateTime requestTimestamp = draftCaptor.getValue().getCreatedAt();
        assertThat(draftCaptor.getValue().getUpdatedAt()).isEqualTo(requestTimestamp);
        assertThat(componentCaptor.getValue().getCreatedAt()).isEqualTo(requestTimestamp);
        assertThat(totalsUpdatedAtCaptor.getValue()).isEqualTo(requestTimestamp);
        verify(mapper, times(2)).selectFinalRecordForUpdate(1001L, "2025-2026");
    }

    @Test
    void shouldFailDeterministicallyWhenInsertedDraftCannotBeReloadedForUpdate() {
        ActivityImportedComponent component = component(2L, 1001L, "S1001", "SPORTS_COMPETITION", "SPORTS", "1.25", "校运会");

        given(mapper.insertDraft(any(FinalRecordDO.class))).willReturn(1);

        assertThatThrownBy(() -> repository.insertActivityComponents("2025-2026", List.of(component)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("最终成绩保存后读取失败");
    }

    @Test
    void shouldReloadDraftAndContinueWhenConcurrentInsertCreatesSameFinalRecord() {
        ActivityImportedComponent component = component(2L, 1001L, "S1001", "SPORTS_COMPETITION", "SPORTS", "1.25", "校运会");
        FinalRecordDO lockedDraft = draftRecord(99001L, 1001L, "2025-2026");

        given(mapper.selectFinalRecordForUpdate(1001L, "2025-2026"))
                .willReturn(null, lockedDraft);
        given(mapper.insertDraft(any(FinalRecordDO.class)))
                .willThrow(new DataIntegrityViolationException(
                        "Duplicate entry '1001-2025-2026' for key 'uk_final_record_student_year'"
                ));
        given(mapper.selectTotals(99001L))
                .willReturn(List.of(total("SPORTS", "1.25")));
        given(mapper.updateTotals(eq(99001L), any(), any(), any(), any(), any(), any(LocalDateTime.class)))
                .willReturn(1);

        repository.insertActivityComponents("2025-2026", List.of(component));

        ArgumentCaptor<ActivityImportedComponentRow> componentCaptor =
                ArgumentCaptor.forClass(ActivityImportedComponentRow.class);
        verify(mapper).insertActivityComponent(componentCaptor.capture());
        assertThat(componentCaptor.getValue().getFinalRecordId()).isEqualTo(99001L);
        verify(mapper, times(2)).selectFinalRecordForUpdate(1001L, "2025-2026");
    }

    @Test
    void shouldRecognizeMysqlDuplicateKeyErrorCodeWhenReloadingConcurrentDraft() {
        ActivityImportedComponent component = component(2L, 1001L, "S1001", "SPORTS_COMPETITION", "SPORTS", "1.25", "校运会");
        FinalRecordDO lockedDraft = draftRecord(99001L, 1001L, "2025-2026");

        given(mapper.selectFinalRecordForUpdate(1001L, "2025-2026"))
                .willReturn(null, lockedDraft);
        given(mapper.insertDraft(any(FinalRecordDO.class)))
                .willThrow(new DataIntegrityViolationException(
                        "insert failed",
                        new SQLException("Duplicate entry", "23000", 1062)
                ));
        given(mapper.selectTotals(99001L))
                .willReturn(List.of(total("SPORTS", "1.25")));
        given(mapper.updateTotals(eq(99001L), any(), any(), any(), any(), any(), any(LocalDateTime.class)))
                .willReturn(1);

        repository.insertActivityComponents("2025-2026", List.of(component));

        ArgumentCaptor<ActivityImportedComponentRow> componentCaptor =
                ArgumentCaptor.forClass(ActivityImportedComponentRow.class);
        verify(mapper).insertActivityComponent(componentCaptor.capture());
        assertThat(componentCaptor.getValue().getFinalRecordId()).isEqualTo(99001L);
        verify(mapper, times(2)).selectFinalRecordForUpdate(1001L, "2025-2026");
    }

    @Test
    void shouldMapSportsTotalsToPhysicalTotalAndRejectUnknownCategoryAsDataAccessException() {
        ActivityImportedComponent component = component(2L, 1001L, "S1001", "SPORTS_COMPETITION", "SPORTS", "1.25", "校运会");
        FinalRecordDO draft = draftRecord(99001L, 1001L, "2025-2026");

        given(mapper.selectFinalRecordForUpdate(1001L, "2025-2026"))
                .willReturn(draft);
        given(mapper.selectTotals(99001L))
                .willReturn(List.of(
                        total("MORAL", "1.00"),
                        total("INTELLECTUAL", "2.00"),
                        total("SPORTS", "3.00"),
                        total("LABOR", "4.00")
                ));
        given(mapper.updateTotals(eq(99001L), any(), any(), any(), any(), any(), any(LocalDateTime.class)))
                .willReturn(1);

        repository.insertActivityComponents("2025-2026", List.of(component));

        verify(mapper).updateTotals(
                eq(99001L),
                eq(new BigDecimal("1.00")),
                eq(new BigDecimal("2.00")),
                eq(new BigDecimal("3.00")),
                eq(new BigDecimal("4.00")),
                eq(new BigDecimal("10.00")),
                any(LocalDateTime.class)
        );

        given(mapper.selectFinalRecordForUpdate(1001L, "2025-2026"))
                .willReturn(draft);
        given(mapper.selectTotals(99001L))
                .willReturn(List.of(total("UNKNOWN", "1.00")));

        assertThatThrownBy(() -> repository.insertActivityComponents("2025-2026", List.of(component)))
                .isInstanceOf(DataAccessException.class)
                .isNotInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("unsupported final record category")
                .hasMessageNotContaining("UNKNOWN");
    }

    private static FinalRecordDO draftRecord(Long id, Long studentUserId, String academicYear) {
        FinalRecordDO record = new FinalRecordDO();
        record.setId(id);
        record.setStudentUserId(studentUserId);
        record.setAcademicYear(academicYear);
        record.setStatus("DRAFT");
        record.setMoralTotal(BigDecimal.ZERO);
        record.setIntellectualTotal(BigDecimal.ZERO);
        record.setPhysicalTotal(BigDecimal.ZERO);
        record.setLaborTotal(BigDecimal.ZERO);
        record.setGrandTotal(BigDecimal.ZERO);
        record.setVersion(0L);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }

    private static ActivityScoreCategoryTotalRow total(String categoryCode, String scoreValue) {
        ActivityScoreCategoryTotalRow row = new ActivityScoreCategoryTotalRow();
        row.setCategoryCode(categoryCode);
        row.setScoreValue(new BigDecimal(scoreValue));
        return row;
    }

    private static ActivityImportedComponent component(Long rowNo,
                                                       Long studentUserId,
                                                       String studentNo,
                                                       String itemCode,
                                                       String categoryCode,
                                                       String scoreValue,
                                                       String displayText) {
        return new ActivityImportedComponent(
                rowNo,
                studentUserId,
                studentNo,
                itemCode,
                categoryCode,
                new BigDecimal(scoreValue),
                displayText,
                displayText,
                "ACTIVITY-20252026-20260518143000-BATCH000001"
        );
    }
}
