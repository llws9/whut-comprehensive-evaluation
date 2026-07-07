package edu.whut.eval.infra.persistence.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.whut.eval.common.exception.SystemException;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.application.model.ApplicationScoringSnapshot;
import edu.whut.eval.domain.application.model.ApplicationSubmission;
import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;
import edu.whut.eval.domain.application.model.AttachmentRef;
import edu.whut.eval.domain.application.repository.ApplicationSubmissionRepository;
import edu.whut.eval.infra.persistence.dataobject.ApplicationAttachmentDO;
import edu.whut.eval.infra.persistence.dataobject.ApplicationFactDO;
import edu.whut.eval.infra.persistence.dataobject.ApplicationSubmissionDO;
import edu.whut.eval.infra.persistence.mapper.ApplicationAttachmentMapper;
import edu.whut.eval.infra.persistence.mapper.ApplicationFactMapper;
import edu.whut.eval.infra.persistence.mapper.ApplicationSubmissionMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * 申请提交聚合仓储的 MyBatis 实现。
 */
@Repository
public class MybatisPlusApplicationSubmissionRepository implements ApplicationSubmissionRepository {

    private static final String EXTRA_OPTION_CODE = "optionCode";
    private static final String EXTRA_MAX_POINTS = "maxPoints";
    private static final String EXTRA_EXCEEDS_MAX_POINTS = "exceedsMaxPoints";

    private final ApplicationSubmissionMapper applicationSubmissionMapper;
    private final ApplicationAttachmentMapper applicationAttachmentMapper;
    private final ApplicationFactMapper applicationFactMapper;
    private final ObjectMapper objectMapper;

    public MybatisPlusApplicationSubmissionRepository(ApplicationSubmissionMapper applicationSubmissionMapper,
                                                      ApplicationAttachmentMapper applicationAttachmentMapper,
                                                      ApplicationFactMapper applicationFactMapper,
                                                      ObjectMapper objectMapper) {
        this.applicationSubmissionMapper = applicationSubmissionMapper;
        this.applicationAttachmentMapper = applicationAttachmentMapper;
        this.applicationFactMapper = applicationFactMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ApplicationSubmission> findById(Long applicationId) {
        ApplicationSubmissionDO applicationSubmissionDO = applicationSubmissionMapper.selectById(applicationId);
        if (applicationSubmissionDO == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(applicationSubmissionDO,
                applicationAttachmentMapper.selectByApplicationId(applicationId),
                applicationFactMapper.selectLatestByApplicationId(applicationId)));
    }

    @Override
    @Transactional
    public ApplicationSubmission save(ApplicationSubmission applicationSubmission) {
        ApplicationSubmissionDO applicationSubmissionDO = toDataObject(applicationSubmission);
        if (applicationSubmission.getApplicationId() == null) {
            applicationSubmissionMapper.insert(applicationSubmissionDO);
        } else {
            long previousVersion = Math.max(0L, applicationSubmission.getVersion() - 1);
            int updated = applicationSubmissionMapper.updateWithVersion(applicationSubmissionDO, previousVersion);
            if (updated == 0) {
                throw new ConflictException("申请版本已变更，请刷新后重试");
            }
            applicationAttachmentMapper.deleteByApplicationId(applicationSubmission.getApplicationId());
        }
        replaceAttachments(applicationSubmissionDO.getApplicationId(), applicationSubmission.getEvidenceAttachments());
        replaceScoringSnapshot(applicationSubmissionDO.getApplicationId(), applicationSubmission.getScoringSnapshot());
        return findById(applicationSubmissionDO.getApplicationId())
                .orElseThrow(() -> new ConflictException("申请保存后读取失败"));
    }

    private void replaceScoringSnapshot(Long applicationId, ApplicationScoringSnapshot scoringSnapshot) {
        applicationFactMapper.deleteByApplicationId(applicationId);
        if (scoringSnapshot == null) {
            return;
        }
        ApplicationFactDO fact = toApplicationFactDO(applicationId, scoringSnapshot);
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        fact.setCreatedAt(now);
        fact.setUpdatedAt(now);
        applicationFactMapper.insert(fact);
    }

    private void replaceAttachments(Long applicationId, List<AttachmentRef> attachments) {
        for (int i = 0; i < attachments.size(); i++) {
            AttachmentRef attachment = attachments.get(i);
            ApplicationAttachmentDO applicationAttachmentDO = new ApplicationAttachmentDO();
            applicationAttachmentDO.setApplicationId(applicationId);
            applicationAttachmentDO.setFileId(attachment.getFileId());
            applicationAttachmentDO.setStorageKey(attachment.getStorageKey());
            applicationAttachmentDO.setOriginalFilename(attachment.getOriginalFilename());
            applicationAttachmentDO.setContentType(attachment.getContentType());
            applicationAttachmentDO.setSize(attachment.getSize());
            applicationAttachmentDO.setUploadedBy(attachment.getUploadedBy());
            applicationAttachmentDO.setSortNo(i);
            applicationAttachmentMapper.insert(applicationAttachmentDO);
        }
    }

    private ApplicationSubmission toDomain(ApplicationSubmissionDO applicationSubmissionDO,
                                           List<ApplicationAttachmentDO> attachmentDOS,
                                           ApplicationFactDO applicationFactDO) {
        return new ApplicationSubmission(
                applicationSubmissionDO.getApplicationId(),
                applicationSubmissionDO.getApplicantUserId(),
                applicationSubmissionDO.getOrgUnitId(),
                applicationSubmissionDO.getCategoryCode(),
                applicationSubmissionDO.getItemCode(),
                applicationSubmissionDO.getAcademicYear(),
                applicationSubmissionDO.getTerm(),
                applicationSubmissionDO.getTitle(),
                applicationSubmissionDO.getDescription(),
                attachmentDOS.stream().map(this::toAttachment).toList(),
                ApplicationSubmissionStatus.valueOf(applicationSubmissionDO.getStatus()),
                toInstant(applicationSubmissionDO.getSubmittedAt()),
                toInstant(applicationSubmissionDO.getCreatedAt()),
                toInstant(applicationSubmissionDO.getUpdatedAt()),
                applicationSubmissionDO.getVersion(),
                toScoringSnapshot(applicationFactDO)
        );
    }

    private AttachmentRef toAttachment(ApplicationAttachmentDO applicationAttachmentDO) {
        Long size = applicationAttachmentDO.getSize();
        if (size == null) {
            throw new IllegalStateException("附件大小不能为空: fileId=" + applicationAttachmentDO.getFileId());
        }
        return new AttachmentRef(
                applicationAttachmentDO.getFileId(),
                applicationAttachmentDO.getStorageKey(),
                applicationAttachmentDO.getOriginalFilename(),
                applicationAttachmentDO.getContentType(),
                size,
                applicationAttachmentDO.getUploadedBy()
        );
    }

    private ApplicationSubmissionDO toDataObject(ApplicationSubmission applicationSubmission) {
        ApplicationSubmissionDO applicationSubmissionDO = new ApplicationSubmissionDO();
        applicationSubmissionDO.setApplicationId(applicationSubmission.getApplicationId());
        applicationSubmissionDO.setApplicantUserId(applicationSubmission.getApplicantUserId());
        applicationSubmissionDO.setOrgUnitId(applicationSubmission.getOrgUnitId());
        applicationSubmissionDO.setCategoryCode(applicationSubmission.getCategoryCode());
        applicationSubmissionDO.setItemCode(applicationSubmission.getItemCode());
        applicationSubmissionDO.setAcademicYear(applicationSubmission.getAcademicYear());
        applicationSubmissionDO.setTerm(applicationSubmission.getTerm());
        applicationSubmissionDO.setTitle(applicationSubmission.getTitle());
        applicationSubmissionDO.setDescription(applicationSubmission.getDescription());
        applicationSubmissionDO.setStatus(applicationSubmission.getStatus().name());
        applicationSubmissionDO.setSubmittedAt(toLocalDateTime(applicationSubmission.getSubmittedAt()));
        applicationSubmissionDO.setCreatedAt(toLocalDateTime(applicationSubmission.getCreatedAt()));
        applicationSubmissionDO.setUpdatedAt(toLocalDateTime(applicationSubmission.getUpdatedAt()));
        applicationSubmissionDO.setVersion(applicationSubmission.getVersion());
        return applicationSubmissionDO;
    }

    private ApplicationFactDO toApplicationFactDO(Long applicationId, ApplicationScoringSnapshot snapshot) {
        ApplicationFactDO applicationFactDO = new ApplicationFactDO();
        applicationFactDO.setApplicationId(applicationId);
        applicationFactDO.setScoreValue(snapshot.appliedPoints());
        applicationFactDO.setDisplayText(snapshot.warningMessage());
        applicationFactDO.setEvidenceCount(snapshot.evidenceCount());
        applicationFactDO.setExtraJson(toExtraJson(snapshot));
        return applicationFactDO;
    }

    private String toExtraJson(ApplicationScoringSnapshot snapshot) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            if (snapshot.optionCode() == null) {
                node.putNull(EXTRA_OPTION_CODE);
            } else {
                node.put(EXTRA_OPTION_CODE, snapshot.optionCode());
            }
            if (snapshot.maxPoints() == null) {
                node.putNull(EXTRA_MAX_POINTS);
            } else {
                node.put(EXTRA_MAX_POINTS, snapshot.maxPoints().toPlainString());
            }
            node.put(EXTRA_EXCEEDS_MAX_POINTS, snapshot.exceedsMaxPoints());
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new SystemException("申请评分快照序列化失败", exception);
        }
    }

    private ApplicationScoringSnapshot toScoringSnapshot(ApplicationFactDO fact) {
        if (fact == null) {
            return null;
        }
        try {
            JsonNode extra = objectMapper.readTree(fact.getExtraJson() == null ? "{}" : fact.getExtraJson());
            String optionCode = extra.path(EXTRA_OPTION_CODE).asText(null);
            String maxPointsText = extra.path(EXTRA_MAX_POINTS).asText(null);
            BigDecimal maxPoints = parseOptionalBigDecimal(maxPointsText);
            boolean exceedsMaxPoints = extra.path(EXTRA_EXCEEDS_MAX_POINTS).asBoolean(false);
            return new ApplicationScoringSnapshot(
                    optionCode,
                    fact.getScoreValue(),
                    maxPoints,
                    fact.getEvidenceCount(),
                    exceedsMaxPoints,
                    fact.getDisplayText()
            );
        } catch (JsonProcessingException | NumberFormatException exception) {
            throw new SystemException("申请评分快照反序列化失败", exception);
        }
    }

    private BigDecimal parseOptionalBigDecimal(String text) {
        return text == null || text.isBlank() ? null : new BigDecimal(text);
    }

    private Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
