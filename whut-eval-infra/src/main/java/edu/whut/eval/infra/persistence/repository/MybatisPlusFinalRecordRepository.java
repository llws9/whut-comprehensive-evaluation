package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.finalrecord.model.FinalComponentScore;
import edu.whut.eval.domain.finalrecord.model.FinalRecord;
import edu.whut.eval.domain.finalrecord.model.FinalRecordStatus;
import edu.whut.eval.domain.finalrecord.repository.AggregatedFinalRecordSnapshot;
import edu.whut.eval.domain.finalrecord.repository.FinalRecordRepository;
import edu.whut.eval.infra.persistence.dataobject.FinalComponentScoreDO;
import edu.whut.eval.infra.persistence.dataobject.FinalRecordDO;
import edu.whut.eval.infra.persistence.mapper.FinalComponentScoreMapper;
import edu.whut.eval.infra.persistence.mapper.FinalRecordAggregationMapper;
import edu.whut.eval.infra.persistence.mapper.FinalRecordMapper;
import edu.whut.eval.infra.persistence.query.ApprovedApplicationFactRow;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class MybatisPlusFinalRecordRepository implements FinalRecordRepository {

    private final FinalRecordMapper finalRecordMapper;
    private final FinalComponentScoreMapper finalComponentScoreMapper;
    private final FinalRecordAggregationMapper finalRecordAggregationMapper;

    public MybatisPlusFinalRecordRepository(FinalRecordMapper finalRecordMapper,
                                            FinalComponentScoreMapper finalComponentScoreMapper,
                                            FinalRecordAggregationMapper finalRecordAggregationMapper) {
        this.finalRecordMapper = finalRecordMapper;
        this.finalComponentScoreMapper = finalComponentScoreMapper;
        this.finalRecordAggregationMapper = finalRecordAggregationMapper;
    }

    @Override
    public Optional<FinalRecord> findByStudentAndAcademicYear(long studentUserId, String academicYear) {
        return Optional.ofNullable(finalRecordMapper.selectByStudentAndAcademicYear(studentUserId, academicYear))
                .map(this::toDomain);
    }

    @Override
    public Optional<FinalRecord> findById(long finalRecordId) {
        return Optional.ofNullable(finalRecordMapper.selectById(finalRecordId)).map(this::toDomain);
    }

    @Override
    public AggregatedFinalRecordSnapshot aggregateApprovedFacts(long studentUserId, String academicYear) {
        List<Long> approvedIds = finalRecordAggregationMapper.selectApprovedApplicationIds(studentUserId, academicYear);
        if (approvedIds.isEmpty()) {
            throw new ConflictException("没有可汇总的已审核申请");
        }
        List<ApprovedApplicationFactRow> facts = finalRecordAggregationMapper.selectApprovedFacts(approvedIds);
        Set<Long> factApplicationIds = new HashSet<>();
        for (ApprovedApplicationFactRow fact : facts) {
            factApplicationIds.add(fact.applicationId());
        }
        if (!factApplicationIds.containsAll(approvedIds)) {
            throw new ConflictException("approved snapshot incomplete");
        }

        BigDecimal moralTotal = BigDecimal.ZERO;
        BigDecimal intellectualTotal = BigDecimal.ZERO;
        BigDecimal physicalTotal = BigDecimal.ZERO;
        BigDecimal laborTotal = BigDecimal.ZERO;
        BigDecimal componentTotal = BigDecimal.ZERO;
        List<FinalComponentScore> components = new ArrayList<>();
        Instant now = Instant.now();

        for (ApprovedApplicationFactRow row : facts) {
            if (row.scoreValue() == null) {
                throw new ConflictException("approved fact score missing");
            }
            switch (row.categoryCode()) {
                case "MORAL" -> moralTotal = moralTotal.add(row.scoreValue());
                case "INTELLECTUAL" -> intellectualTotal = intellectualTotal.add(row.scoreValue());
                case "SPORTS" -> physicalTotal = physicalTotal.add(row.scoreValue());
                case "LABOR" -> laborTotal = laborTotal.add(row.scoreValue());
                default -> throw new ConflictException("unsupported final record category: " + row.categoryCode());
            }
            componentTotal = componentTotal.add(row.scoreValue());
            components.add(new FinalComponentScore(null, null, row.categoryCode(), row.itemCode(), row.scoreValue(),
                    row.displayText(), row.sourceType(), row.sourceRefId(), now));
        }

        BigDecimal grandTotal = moralTotal.add(intellectualTotal).add(physicalTotal).add(laborTotal);
        if (grandTotal.compareTo(componentTotal) != 0) {
            throw new ConflictException("final record total mismatch");
        }
        return new AggregatedFinalRecordSnapshot(moralTotal, intellectualTotal, physicalTotal, laborTotal, grandTotal, components);
    }

    @Override
    public FinalRecord insertDraft(FinalRecord record) {
        FinalRecordDO dataObject = toDataObject(record);
        try {
            finalRecordMapper.insert(dataObject);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("最终成绩已存在，不能重复汇总");
        }
        return findById(dataObject.getId()).orElseThrow(() -> new ConflictException("最终成绩保存后读取失败"));
    }

    @Override
    public void deleteDraft(long finalRecordId) {
        finalRecordMapper.deleteDraft(finalRecordId);
    }

    @Override
    public void deleteComponents(long finalRecordId) {
        finalComponentScoreMapper.deleteByFinalRecordId(finalRecordId);
    }

    @Override
    public void batchInsertComponents(long finalRecordId, List<FinalComponentScore> components) {
        for (FinalComponentScore component : components) {
            finalComponentScoreMapper.insert(toDataObject(finalRecordId, component));
        }
    }

    @Override
    public FinalRecord updateTransition(FinalRecord record) {
        FinalRecordDO dataObject = toDataObject(record);
        long previousVersion = Math.max(0L, record.getVersion() - 1);
        int updated = finalRecordMapper.updateTransition(dataObject, previousVersion);
        if (updated == 0) {
            throw new ConflictException("最终成绩版本已变更，请刷新后重试");
        }
        return findById(record.getId()).orElseThrow(() -> new ConflictException("最终成绩更新后读取失败"));
    }

    @Override
    public List<FinalComponentScore> listComponents(long finalRecordId) {
        return finalComponentScoreMapper.selectByFinalRecordId(finalRecordId).stream()
                .map(this::toDomain)
                .toList();
    }

    private FinalRecord toDomain(FinalRecordDO dataObject) {
        return new FinalRecord(
                dataObject.getId(),
                dataObject.getStudentUserId(),
                dataObject.getAcademicYear(),
                FinalRecordStatus.valueOf(dataObject.getStatus()),
                dataObject.getMoralTotal(),
                dataObject.getIntellectualTotal(),
                dataObject.getPhysicalTotal(),
                dataObject.getLaborTotal(),
                dataObject.getGrandTotal(),
                toInstant(dataObject.getSubmittedAt()),
                toInstant(dataObject.getConfirmedAt()),
                dataObject.getConfirmComment(),
                dataObject.getVersion(),
                toInstant(dataObject.getCreatedAt()),
                toInstant(dataObject.getUpdatedAt())
        );
    }

    private FinalComponentScore toDomain(FinalComponentScoreDO dataObject) {
        return new FinalComponentScore(
                dataObject.getId(),
                dataObject.getFinalRecordId(),
                dataObject.getCategoryCode(),
                dataObject.getItemCode(),
                dataObject.getScoreValue(),
                dataObject.getDisplayText(),
                dataObject.getSourceType(),
                dataObject.getSourceRefId(),
                toInstant(dataObject.getCreatedAt())
        );
    }

    private FinalRecordDO toDataObject(FinalRecord record) {
        FinalRecordDO dataObject = new FinalRecordDO();
        dataObject.setId(record.getId());
        dataObject.setStudentUserId(record.getStudentUserId());
        dataObject.setAcademicYear(record.getAcademicYear());
        dataObject.setStatus(record.getStatus().name());
        dataObject.setMoralTotal(record.getMoralTotal());
        dataObject.setIntellectualTotal(record.getIntellectualTotal());
        dataObject.setPhysicalTotal(record.getPhysicalTotal());
        dataObject.setLaborTotal(record.getLaborTotal());
        dataObject.setGrandTotal(record.getGrandTotal());
        dataObject.setSubmittedAt(toLocalDateTime(record.getSubmittedAt()));
        dataObject.setConfirmedAt(toLocalDateTime(record.getConfirmedAt()));
        dataObject.setConfirmComment(record.getConfirmComment());
        dataObject.setVersion(record.getVersion());
        dataObject.setCreatedAt(toLocalDateTime(record.getCreatedAt()));
        dataObject.setUpdatedAt(toLocalDateTime(record.getUpdatedAt()));
        return dataObject;
    }

    private FinalComponentScoreDO toDataObject(long finalRecordId, FinalComponentScore component) {
        FinalComponentScoreDO dataObject = new FinalComponentScoreDO();
        dataObject.setFinalRecordId(finalRecordId);
        dataObject.setCategoryCode(component.getCategoryCode());
        dataObject.setItemCode(component.getItemCode());
        dataObject.setScoreValue(component.getScoreValue());
        dataObject.setDisplayText(component.getDisplayText());
        dataObject.setSourceType(component.getSourceType());
        dataObject.setSourceRefId(component.getSourceRefId());
        dataObject.setCreatedAt(toLocalDateTime(component.getCreatedAt()));
        return dataObject;
    }

    private Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
