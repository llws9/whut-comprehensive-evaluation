package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.application.finalrecord.importing.LectureImportRepository;
import edu.whut.eval.application.finalrecord.importing.LectureImportStudentTarget;
import edu.whut.eval.application.finalrecord.importing.LectureImportedComponent;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.finalrecord.importing.LectureImportFailedRow;
import edu.whut.eval.infra.persistence.dataobject.FinalRecordDO;
import edu.whut.eval.infra.persistence.mapper.LectureImportMapper;
import edu.whut.eval.infra.persistence.repository.row.LectureImportStudentTargetRow;
import edu.whut.eval.infra.persistence.repository.row.LectureImportedComponentRow;
import edu.whut.eval.infra.persistence.repository.row.LectureScoreCategoryTotalRow;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
public class MybatisLectureImportRepository implements LectureImportRepository {

    private static final String CATEGORY_CODE = "INTELLECTUAL";
    private static final String ITEM_CODE = "INTELLECTUAL_LECTURE";

    private final LectureImportMapper mapper;

    public MybatisLectureImportRepository(LectureImportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean lectureBatchExists(String academicYear, String lectureBatchId) {
        return mapper.countLectureBatchComponents(academicYear, lectureBatchId) > 0;
    }

    @Override
    public Optional<LectureImportStudentTarget> findTarget(String studentNo, String academicYear) {
        return Optional.ofNullable(mapper.selectTarget(studentNo))
                .map(this::toTarget);
    }

    @Override
    public Optional<String> findActiveOrgPath(Long orgUnitId) {
        return Optional.ofNullable(mapper.selectActiveOrgPath(orgUnitId));
    }

    @Override
    @Transactional
    public List<LectureImportFailedRow> insertLectureComponents(String academicYear,
                                                                String lectureBatchId,
                                                                List<LectureImportedComponent> components) {
        List<LectureImportFailedRow> failures = new ArrayList<>();
        LocalDateTime requestTimestamp = LocalDateTime.now();
        for (LectureImportedComponent component : components) {
            FinalRecordDO record = mapper.selectFinalRecordForUpdate(component.studentUserId(), academicYear);
            if (record == null) {
                record = insertOrReloadDraft(academicYear, component, requestTimestamp);
            }
            if (!"DRAFT".equals(record.getStatus())) {
                failures.add(lockedFailure(component));
                continue;
            }

            mapper.insertLectureComponent(toComponentRow(record.getId(), lectureBatchId, component, requestTimestamp));
            updateTotals(record.getId(), requestTimestamp);
        }
        return List.copyOf(failures);
    }

    private LectureImportStudentTarget toTarget(LectureImportStudentTargetRow row) {
        return new LectureImportStudentTarget(
                row.getStudentUserId(),
                row.getStudentNo(),
                row.getOrgUnitId(),
                row.getOrgPath()
        );
    }

    private FinalRecordDO insertOrReloadDraft(String academicYear,
                                              LectureImportedComponent component,
                                              LocalDateTime requestTimestamp) {
        FinalRecordDO record = newDraftRecord(academicYear, component.studentUserId(), requestTimestamp);
        try {
            mapper.insertDraft(record);
            return reloadDraftForUpdate(academicYear, component);
        } catch (DataIntegrityViolationException exception) {
            if (!isDuplicateFinalRecord(exception)) {
                throw exception;
            }
            return reloadDraftForUpdate(academicYear, component);
        }
    }

    private FinalRecordDO reloadDraftForUpdate(String academicYear, LectureImportedComponent component) {
        FinalRecordDO record = mapper.selectFinalRecordForUpdate(component.studentUserId(), academicYear);
        if (record == null) {
            throw new ConflictException("最终成绩保存后读取失败");
        }
        return record;
    }

    private boolean isDuplicateFinalRecord(DataIntegrityViolationException exception) {
        String message = String.valueOf(exception.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        return message.contains("uk_final_record_student_year")
                || (message.contains("duplicate") && message.contains("final_record"));
    }

    private FinalRecordDO newDraftRecord(String academicYear, Long studentUserId, LocalDateTime requestTimestamp) {
        FinalRecordDO record = new FinalRecordDO();
        record.setStudentUserId(studentUserId);
        record.setAcademicYear(academicYear);
        record.setStatus("DRAFT");
        record.setMoralTotal(scale(BigDecimal.ZERO));
        record.setIntellectualTotal(scale(BigDecimal.ZERO));
        record.setPhysicalTotal(scale(BigDecimal.ZERO));
        record.setLaborTotal(scale(BigDecimal.ZERO));
        record.setGrandTotal(scale(BigDecimal.ZERO));
        record.setSubmittedAt(null);
        record.setConfirmedAt(null);
        record.setConfirmComment(null);
        record.setVersion(0L);
        record.setCreatedAt(requestTimestamp);
        record.setUpdatedAt(requestTimestamp);
        return record;
    }

    private LectureImportedComponentRow toComponentRow(Long finalRecordId,
                                                       String lectureBatchId,
                                                       LectureImportedComponent component,
                                                       LocalDateTime now) {
        LectureImportedComponentRow row = new LectureImportedComponentRow();
        row.setFinalRecordId(finalRecordId);
        row.setCategoryCode(CATEGORY_CODE);
        row.setItemCode(ITEM_CODE);
        row.setScoreValue(scale(component.scoreValue()));
        row.setDisplayText(component.displayText());
        row.setSourceRefId(lectureBatchId);
        row.setCreatedAt(now);
        return row;
    }

    private LectureImportFailedRow lockedFailure(LectureImportedComponent component) {
        Map<String, String> rawValue = new LinkedHashMap<>();
        rawValue.put("studentNo", component.studentNo());
        rawValue.put("scoreValue", component.scoreValueText());
        rawValue.put("displayText", component.rawDisplayText());
        return new LectureImportFailedRow(
                component.rowNo(),
                "FINAL_RECORD_LOCKED",
                "已提交或已确认的最终成绩不允许导入覆盖",
                rawValue
        );
    }

    private void updateTotals(Long finalRecordId, LocalDateTime updatedAt) {
        BigDecimal moral = BigDecimal.ZERO;
        BigDecimal intellectual = BigDecimal.ZERO;
        BigDecimal physical = BigDecimal.ZERO;
        BigDecimal labor = BigDecimal.ZERO;

        for (LectureScoreCategoryTotalRow total : mapper.selectTotals(finalRecordId)) {
            BigDecimal value = scale(total.getScoreValue());
            switch (total.getCategoryCode()) {
                case "MORAL" -> moral = value;
                case "INTELLECTUAL" -> intellectual = value;
                case "SPORTS" -> physical = value;
                case "LABOR" -> labor = value;
                default -> throw new ConflictException("unsupported final record category: " + total.getCategoryCode());
            }
        }

        BigDecimal grand = scale(moral.add(intellectual).add(physical).add(labor));
        int updated = mapper.updateTotals(
                finalRecordId,
                scale(moral),
                scale(intellectual),
                scale(physical),
                scale(labor),
                grand,
                updatedAt
        );
        if (updated == 0) {
            throw new ConflictException("最终成绩状态已变更，请刷新后重试");
        }
    }

    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
