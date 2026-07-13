package edu.whut.eval.infra.persistence.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.finalrecord.importing.ActivityImportItemDefinition;
import edu.whut.eval.application.finalrecord.importing.ActivityImportRepository;
import edu.whut.eval.application.finalrecord.importing.ActivityImportStudentTarget;
import edu.whut.eval.application.finalrecord.importing.ActivityImportedComponent;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.finalrecord.importing.ActivityImportFailedRow;
import edu.whut.eval.infra.persistence.dataobject.FinalRecordDO;
import edu.whut.eval.infra.persistence.mapper.ActivityImportMapper;
import edu.whut.eval.infra.persistence.repository.row.ActivityImportItemDefinitionRow;
import edu.whut.eval.infra.persistence.repository.row.ActivityImportStudentTargetRow;
import edu.whut.eval.infra.persistence.repository.row.ActivityImportedComponentRow;
import edu.whut.eval.infra.persistence.repository.row.ActivityScoreCategoryTotalRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
public class MybatisActivityImportRepository implements ActivityImportRepository {

    private static final BigDecimal MAX_ALLOWED_POINTS = new BigDecimal("99999999.99");

    private final ActivityImportMapper mapper;
    private final ObjectMapper objectMapper;

    @Autowired
    public MybatisActivityImportRepository(ActivityImportMapper mapper) {
        this(mapper, new ObjectMapper());
    }

    public MybatisActivityImportRepository(ActivityImportMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ActivityImportItemDefinition> findActiveSportsItem(String itemCode) {
        return Optional.ofNullable(mapper.selectActiveSportsItem(itemCode))
                .flatMap(this::toItemDefinition);
    }

    @Override
    public boolean activityBatchExists(String academicYear, String categoryCode, String itemCode, String activityBatchId) {
        return mapper.countActivityBatchComponents(academicYear, categoryCode, itemCode, activityBatchId) > 0;
    }

    @Override
    public Optional<ActivityImportStudentTarget> findTarget(String studentNo, String academicYear) {
        return Optional.ofNullable(mapper.selectTarget(studentNo))
                .map(this::toTarget);
    }

    @Override
    public Optional<String> findActiveOrgPath(Long orgUnitId) {
        return Optional.ofNullable(mapper.selectActiveOrgPath(orgUnitId));
    }

    @Override
    @Transactional
    public List<ActivityImportFailedRow> insertActivityComponents(String academicYear,
                                                                  List<ActivityImportedComponent> components) {
        List<ActivityImportFailedRow> failures = new ArrayList<>();
        LocalDateTime requestTimestamp = LocalDateTime.now();
        for (ActivityImportedComponent component : components) {
            FinalRecordDO record = mapper.selectFinalRecordForUpdate(component.studentUserId(), academicYear);
            if (record == null) {
                record = insertOrReloadDraft(academicYear, component, requestTimestamp);
            }
            if (!"DRAFT".equals(record.getStatus())) {
                failures.add(lockedFailure(component));
                continue;
            }

            mapper.insertActivityComponent(toComponentRow(record.getId(), component, requestTimestamp));
            updateTotals(record.getId(), requestTimestamp);
        }
        return List.copyOf(failures);
    }

    private Optional<ActivityImportItemDefinition> toItemDefinition(ActivityImportItemDefinitionRow row) {
        String capRuleJson = row.getCapRuleJson();
        if (capRuleJson == null || capRuleJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(capRuleJson);
            if (!root.isObject()) {
                return Optional.empty();
            }
            JsonNode maxPointsNode = root.get("maxPoints");
            JsonNode allowOverflowNode = root.get("allowOverflow");
            if (maxPointsNode == null
                    || !maxPointsNode.isNumber()
                    || allowOverflowNode == null
                    || !allowOverflowNode.isBoolean()) {
                return Optional.empty();
            }
            BigDecimal maxPoints = maxPointsNode.decimalValue();
            if (maxPoints.compareTo(BigDecimal.ZERO) < 0 || maxPoints.compareTo(MAX_ALLOWED_POINTS) > 0) {
                return Optional.empty();
            }
            return Optional.of(new ActivityImportItemDefinition(
                    row.getItemCode(),
                    row.getCategoryCode(),
                    maxPoints,
                    allowOverflowNode.booleanValue()
            ));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private ActivityImportStudentTarget toTarget(ActivityImportStudentTargetRow row) {
        return new ActivityImportStudentTarget(
                row.getStudentUserId(),
                row.getStudentNo(),
                row.getOrgUnitId()
        );
    }

    private FinalRecordDO insertOrReloadDraft(String academicYear,
                                              ActivityImportedComponent component,
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

    private FinalRecordDO reloadDraftForUpdate(String academicYear, ActivityImportedComponent component) {
        FinalRecordDO record = mapper.selectFinalRecordForUpdate(component.studentUserId(), academicYear);
        if (record == null) {
            throw new ConflictException("最终成绩保存后读取失败");
        }
        return record;
    }

    private boolean isDuplicateFinalRecord(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && (sqlException.getErrorCode() == 1062 || "23000".equals(sqlException.getSQLState()))) {
                return true;
            }
            current = current.getCause();
        }
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

    private ActivityImportedComponentRow toComponentRow(Long finalRecordId,
                                                        ActivityImportedComponent component,
                                                        LocalDateTime now) {
        ActivityImportedComponentRow row = new ActivityImportedComponentRow();
        row.setFinalRecordId(finalRecordId);
        row.setCategoryCode(component.categoryCode());
        row.setItemCode(component.canonicalItemCode());
        row.setScoreValue(scale(component.scoreValue()));
        row.setDisplayText(component.displayText());
        row.setSourceRefId(component.activityBatchId());
        row.setCreatedAt(now);
        return row;
    }

    private ActivityImportFailedRow lockedFailure(ActivityImportedComponent component) {
        Map<String, String> rawValue = new LinkedHashMap<>();
        rawValue.put("studentNo", component.studentNo());
        rawValue.put("displayText", component.rawDisplayText());
        return new ActivityImportFailedRow(
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

        for (ActivityScoreCategoryTotalRow total : mapper.selectTotals(finalRecordId)) {
            BigDecimal value = scale(total.getScoreValue());
            switch (total.getCategoryCode()) {
                case "MORAL" -> moral = value;
                case "INTELLECTUAL" -> intellectual = value;
                case "SPORTS" -> physical = value;
                case "LABOR" -> labor = value;
                default -> throw new DataAccessResourceFailureException("unsupported final record category");
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
