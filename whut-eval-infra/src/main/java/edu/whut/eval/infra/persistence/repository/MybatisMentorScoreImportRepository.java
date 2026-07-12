package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.application.finalrecord.importing.MentorScoreImportRepository;
import edu.whut.eval.application.finalrecord.importing.MentorScoreImportStudentTarget;
import edu.whut.eval.application.finalrecord.importing.MentorScoreImportedComponent;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.infra.persistence.dataobject.FinalRecordDO;
import edu.whut.eval.infra.persistence.mapper.MentorScoreImportMapper;
import edu.whut.eval.infra.persistence.repository.row.MentorScoreCategoryTotalRow;
import edu.whut.eval.infra.persistence.repository.row.MentorScoreImportStudentTargetRow;
import edu.whut.eval.infra.persistence.repository.row.MentorScoreImportedComponentRow;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MybatisMentorScoreImportRepository implements MentorScoreImportRepository {

    private final MentorScoreImportMapper mapper;

    public MybatisMentorScoreImportRepository(MentorScoreImportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<MentorScoreImportStudentTarget> findTarget(String studentNo, String academicYear) {
        return Optional.ofNullable(mapper.selectTarget(studentNo, academicYear))
                .map(this::toTarget);
    }

    @Override
    public Optional<String> findActiveOrgPath(Long orgUnitId) {
        return Optional.ofNullable(mapper.selectActiveOrgPath(orgUnitId));
    }

    @Override
    public boolean importedComponentExists(Long studentUserId, String academicYear, String categoryCode, String itemCode) {
        FinalRecordDO record = mapper.selectFinalRecordForUpdate(studentUserId, academicYear);
        return record != null && mapper.selectImportedComponent(record.getId(), categoryCode, itemCode) != null;
    }

    @Override
    @Transactional
    public void upsertDraftComponent(MentorScoreImportedComponent component, String importBatchId) {
        FinalRecordDO record = mapper.selectFinalRecordForUpdate(component.studentUserId(), component.academicYear());
        if (record == null) {
            record = newDraftRecord(component);
            mapper.insertDraft(record);
        }
        if (!"DRAFT".equals(record.getStatus())) {
            throw new ConflictException("已提交或已确认的最终成绩不允许导入覆盖");
        }

        LocalDateTime now = LocalDateTime.now();
        MentorScoreImportedComponentRow existing = mapper.selectImportedComponent(
                record.getId(),
                component.categoryCode(),
                component.itemCode()
        );
        MentorScoreImportedComponentRow componentRow = toComponentRow(record.getId(), component, now);
        if (existing == null) {
            mapper.insertImportedComponent(componentRow);
        } else {
            componentRow.setId(existing.getId());
            mapper.updateImportedComponent(componentRow);
        }
        updateTotals(record.getId(), now);
    }

    private MentorScoreImportStudentTarget toTarget(MentorScoreImportStudentTargetRow row) {
        return new MentorScoreImportStudentTarget(
                row.getStudentUserId(),
                row.getStudentNo(),
                row.getOrgUnitId(),
                row.getOrgPath(),
                row.getFinalRecordStatus()
        );
    }

    private FinalRecordDO newDraftRecord(MentorScoreImportedComponent component) {
        LocalDateTime now = LocalDateTime.now();
        FinalRecordDO record = new FinalRecordDO();
        record.setStudentUserId(component.studentUserId());
        record.setAcademicYear(component.academicYear());
        record.setStatus("DRAFT");
        record.setMoralTotal(BigDecimal.ZERO);
        record.setIntellectualTotal(BigDecimal.ZERO);
        record.setPhysicalTotal(BigDecimal.ZERO);
        record.setLaborTotal(BigDecimal.ZERO);
        record.setGrandTotal(BigDecimal.ZERO);
        record.setSubmittedAt(null);
        record.setConfirmedAt(null);
        record.setConfirmComment(null);
        record.setVersion(0L);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    private MentorScoreImportedComponentRow toComponentRow(Long finalRecordId,
                                                           MentorScoreImportedComponent component,
                                                           LocalDateTime now) {
        MentorScoreImportedComponentRow row = new MentorScoreImportedComponentRow();
        row.setFinalRecordId(finalRecordId);
        row.setCategoryCode(component.categoryCode());
        row.setItemCode(component.itemCode());
        row.setScoreValue(component.scoreValue());
        row.setDisplayText(component.displayText());
        row.setSourceRefId(component.sourceRefId());
        row.setCreatedAt(now);
        return row;
    }

    private void updateTotals(Long finalRecordId, LocalDateTime updatedAt) {
        BigDecimal moral = BigDecimal.ZERO;
        BigDecimal intellectual = BigDecimal.ZERO;
        BigDecimal physical = BigDecimal.ZERO;
        BigDecimal labor = BigDecimal.ZERO;

        List<MentorScoreCategoryTotalRow> totals = mapper.selectTotals(finalRecordId);
        for (MentorScoreCategoryTotalRow total : totals) {
            BigDecimal value = total.getScoreValue() == null ? BigDecimal.ZERO : total.getScoreValue();
            switch (total.getCategoryCode()) {
                case "MORAL" -> moral = value;
                case "INTELLECTUAL" -> intellectual = value;
                case "SPORTS" -> physical = value;
                case "LABOR" -> labor = value;
                default -> throw new ConflictException("unsupported final record category: " + total.getCategoryCode());
            }
        }

        BigDecimal grand = moral.add(intellectual).add(physical).add(labor);
        int updated = mapper.updateTotals(finalRecordId, moral, intellectual, physical, labor, grand, updatedAt);
        if (updated == 0) {
            throw new ConflictException("最终成绩状态已变更，请刷新后重试");
        }
    }
}
