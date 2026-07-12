package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.finalrecord.importing.MentorScoreImportedComponent;
import edu.whut.eval.infra.persistence.dataobject.FinalRecordDO;
import edu.whut.eval.infra.persistence.mapper.MentorScoreImportMapper;
import edu.whut.eval.infra.persistence.repository.MybatisMentorScoreImportRepository;
import edu.whut.eval.infra.persistence.repository.row.MentorScoreCategoryTotalRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MybatisMentorScoreImportRepositoryTest {

    @Mock
    private MentorScoreImportMapper mapper;

    @InjectMocks
    private MybatisMentorScoreImportRepository repository;

    @Test
    void shouldRecoverWhenConcurrentDraftInsertAlreadyCreatedRecord() {
        MentorScoreImportedComponent component = new MentorScoreImportedComponent(
                2L,
                1001L,
                "2025-2026",
                "MORAL",
                "MORAL_HONOR",
                new BigDecimal("1.25"),
                "导师评分",
                "mentor-001"
        );
        FinalRecordDO concurrentDraft = draftRecord(41001L, 1001L, "2025-2026");

        given(mapper.selectFinalRecordForUpdate(1001L, "2025-2026"))
                .willReturn(null, concurrentDraft);
        given(mapper.insertDraft(any(FinalRecordDO.class)))
                .willThrow(new DataIntegrityViolationException("duplicate final_record"));
        given(mapper.selectImportedComponent(41001L, "MORAL", "MORAL_HONOR"))
                .willReturn(null);
        given(mapper.selectTotals(41001L))
                .willReturn(List.of(total("MORAL", "1.25")));
        given(mapper.updateTotals(eq(41001L), any(), any(), any(), any(), any(), any(LocalDateTime.class)))
                .willReturn(1);

        repository.upsertDraftComponent(component, "D7-batch");

        verify(mapper).insertImportedComponent(any());
        verify(mapper).updateTotals(eq(41001L), any(), any(), any(), any(), any(), any(LocalDateTime.class));
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

    private static MentorScoreCategoryTotalRow total(String categoryCode, String scoreValue) {
        MentorScoreCategoryTotalRow row = new MentorScoreCategoryTotalRow();
        row.setCategoryCode(categoryCode);
        row.setScoreValue(new BigDecimal(scoreValue));
        return row;
    }
}
