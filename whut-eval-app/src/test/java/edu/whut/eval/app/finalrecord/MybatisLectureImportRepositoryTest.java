package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.finalrecord.importing.LectureImportedComponent;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.infra.persistence.dataobject.FinalRecordDO;
import edu.whut.eval.infra.persistence.mapper.LectureImportMapper;
import edu.whut.eval.infra.persistence.repository.MybatisLectureImportRepository;
import edu.whut.eval.infra.persistence.repository.row.LectureImportedComponentRow;
import edu.whut.eval.infra.persistence.repository.row.LectureScoreCategoryTotalRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class MybatisLectureImportRepositoryTest {

    @Mock
    private LectureImportMapper mapper;

    @InjectMocks
    private MybatisLectureImportRepository repository;

    @Test
    void shouldReloadInsertedDraftForUpdateBeforeWritingLectureComponent() {
        LectureImportedComponent component = new LectureImportedComponent(
                2L,
                1001L,
                "S1001",
                "1.25",
                new BigDecimal("1.25"),
                "讲座",
                "讲座"
        );
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
                .willReturn(List.of(total("INTELLECTUAL", "1.25")));
        given(mapper.updateTotals(eq(99001L), any(), any(), any(), any(), any(), any(LocalDateTime.class)))
                .willReturn(1);

        repository.insertLectureComponents("2025-2026", "LECTURE-20252026-20260518143000-LOCKRELOAD01", List.of(component));

        ArgumentCaptor<LectureImportedComponentRow> componentCaptor =
                ArgumentCaptor.forClass(LectureImportedComponentRow.class);
        verify(mapper).insertLectureComponent(componentCaptor.capture());
        assertThat(componentCaptor.getValue().getFinalRecordId()).isEqualTo(99001L);
        verify(mapper).selectTotals(99001L);
        verify(mapper).updateTotals(eq(99001L), any(), any(), any(), any(), any(), any(LocalDateTime.class));
    }

    @Test
    void shouldUseOneAuditTimestampForCreatedDraftComponentAndTotalsInSingleRequest() {
        LectureImportedComponent component = new LectureImportedComponent(
                2L,
                1001L,
                "S1001",
                "1.25",
                new BigDecimal("1.25"),
                "讲座",
                "讲座"
        );

        given(mapper.selectFinalRecordForUpdate(1001L, "2025-2026"))
                .willReturn(null)
                .willAnswer(invocation -> {
                    FinalRecordDO inserted = draftRecord(99001L, 1001L, "2025-2026");
                    inserted.setCreatedAt(LocalDateTime.now().plusSeconds(1));
                    inserted.setUpdatedAt(inserted.getCreatedAt());
                    return inserted;
                });
        given(mapper.insertDraft(any(FinalRecordDO.class))).willReturn(1);
        given(mapper.selectTotals(99001L))
                .willReturn(List.of(total("INTELLECTUAL", "1.25")));
        given(mapper.updateTotals(eq(99001L), any(), any(), any(), any(), any(), any(LocalDateTime.class)))
                .willReturn(1);

        repository.insertLectureComponents("2025-2026", "LECTURE-20252026-20260518143000-AUDITTIME01", List.of(component));

        ArgumentCaptor<FinalRecordDO> draftCaptor = ArgumentCaptor.forClass(FinalRecordDO.class);
        ArgumentCaptor<LectureImportedComponentRow> componentCaptor =
                ArgumentCaptor.forClass(LectureImportedComponentRow.class);
        ArgumentCaptor<LocalDateTime> totalsUpdatedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).insertDraft(draftCaptor.capture());
        verify(mapper).insertLectureComponent(componentCaptor.capture());
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
        LectureImportedComponent component = new LectureImportedComponent(
                2L,
                1001L,
                "S1001",
                "1.25",
                new BigDecimal("1.25"),
                "讲座",
                "讲座"
        );

        given(mapper.insertDraft(any(FinalRecordDO.class))).willReturn(1);

        assertThatThrownBy(() -> repository.insertLectureComponents(
                "2025-2026",
                "LECTURE-20252026-20260518143000-LOCKRELOAD02",
                List.of(component)
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("最终成绩保存后读取失败");
    }

    @Test
    void shouldReloadDraftAndContinueWhenConcurrentInsertCreatesSameFinalRecord() {
        LectureImportedComponent component = new LectureImportedComponent(
                2L,
                1001L,
                "S1001",
                "1.25",
                new BigDecimal("1.25"),
                "讲座",
                "讲座"
        );
        FinalRecordDO lockedDraft = draftRecord(99001L, 1001L, "2025-2026");

        given(mapper.selectFinalRecordForUpdate(1001L, "2025-2026"))
                .willReturn(null, lockedDraft);
        given(mapper.insertDraft(any(FinalRecordDO.class)))
                .willThrow(new DataIntegrityViolationException(
                        "Duplicate entry '1001-2025-2026' for key 'uk_final_record_student_year'"
                ));
        given(mapper.selectTotals(99001L))
                .willReturn(List.of(total("INTELLECTUAL", "1.25")));
        given(mapper.updateTotals(eq(99001L), any(), any(), any(), any(), any(), any(LocalDateTime.class)))
                .willReturn(1);

        repository.insertLectureComponents("2025-2026", "LECTURE-20252026-20260518143000-DUPRELOAD01", List.of(component));

        ArgumentCaptor<LectureImportedComponentRow> componentCaptor =
                ArgumentCaptor.forClass(LectureImportedComponentRow.class);
        verify(mapper).insertLectureComponent(componentCaptor.capture());
        assertThat(componentCaptor.getValue().getFinalRecordId()).isEqualTo(99001L);
        verify(mapper, times(2)).selectFinalRecordForUpdate(1001L, "2025-2026");
        verify(mapper).selectTotals(99001L);
        verify(mapper).updateTotals(eq(99001L), any(), any(), any(), any(), any(), any(LocalDateTime.class));
    }

    @Test
    void shouldRecognizeMysqlDuplicateKeyErrorCodeWhenReloadingConcurrentDraft() {
        LectureImportedComponent component = new LectureImportedComponent(
                2L,
                1001L,
                "S1001",
                "1.25",
                new BigDecimal("1.25"),
                "讲座",
                "讲座"
        );
        FinalRecordDO lockedDraft = draftRecord(99001L, 1001L, "2025-2026");

        given(mapper.selectFinalRecordForUpdate(1001L, "2025-2026"))
                .willReturn(null, lockedDraft);
        given(mapper.insertDraft(any(FinalRecordDO.class)))
                .willThrow(new DataIntegrityViolationException(
                        "insert failed",
                        new SQLException("Duplicate entry", "23000", 1062)
                ));
        given(mapper.selectTotals(99001L))
                .willReturn(List.of(total("INTELLECTUAL", "1.25")));
        given(mapper.updateTotals(eq(99001L), any(), any(), any(), any(), any(), any(LocalDateTime.class)))
                .willReturn(1);

        repository.insertLectureComponents("2025-2026", "LECTURE-20252026-20260518143000-DUPRELOAD02", List.of(component));

        ArgumentCaptor<LectureImportedComponentRow> componentCaptor =
                ArgumentCaptor.forClass(LectureImportedComponentRow.class);
        verify(mapper).insertLectureComponent(componentCaptor.capture());
        assertThat(componentCaptor.getValue().getFinalRecordId()).isEqualTo(99001L);
        verify(mapper, times(2)).selectFinalRecordForUpdate(1001L, "2025-2026");
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

    private static LectureScoreCategoryTotalRow total(String categoryCode, String scoreValue) {
        LectureScoreCategoryTotalRow row = new LectureScoreCategoryTotalRow();
        row.setCategoryCode(categoryCode);
        row.setScoreValue(new BigDecimal(scoreValue));
        return row;
    }
}
