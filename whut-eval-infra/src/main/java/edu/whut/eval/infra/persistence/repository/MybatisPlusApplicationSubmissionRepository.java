package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.application.model.ApplicationSubmission;
import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;
import edu.whut.eval.domain.application.model.AttachmentRef;
import edu.whut.eval.domain.application.repository.ApplicationSubmissionRepository;
import edu.whut.eval.infra.persistence.dataobject.ApplicationAttachmentDO;
import edu.whut.eval.infra.persistence.dataobject.ApplicationSubmissionDO;
import edu.whut.eval.infra.persistence.mapper.ApplicationAttachmentMapper;
import edu.whut.eval.infra.persistence.mapper.ApplicationSubmissionMapper;
import org.springframework.stereotype.Repository;

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

    private final ApplicationSubmissionMapper applicationSubmissionMapper;
    private final ApplicationAttachmentMapper applicationAttachmentMapper;

    public MybatisPlusApplicationSubmissionRepository(ApplicationSubmissionMapper applicationSubmissionMapper,
                                                      ApplicationAttachmentMapper applicationAttachmentMapper) {
        this.applicationSubmissionMapper = applicationSubmissionMapper;
        this.applicationAttachmentMapper = applicationAttachmentMapper;
    }

    @Override
    public Optional<ApplicationSubmission> findById(Long applicationId) {
        ApplicationSubmissionDO applicationSubmissionDO = applicationSubmissionMapper.selectById(applicationId);
        if (applicationSubmissionDO == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(applicationSubmissionDO,
                applicationAttachmentMapper.selectByApplicationId(applicationId)));
    }

    @Override
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
        return findById(applicationSubmissionDO.getApplicationId())
                .orElseThrow(() -> new ConflictException("申请保存后读取失败"));
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
                                           List<ApplicationAttachmentDO> attachmentDOS) {
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
                applicationSubmissionDO.getVersion()
        );
    }

    private AttachmentRef toAttachment(ApplicationAttachmentDO applicationAttachmentDO) {
        return new AttachmentRef(
                applicationAttachmentDO.getFileId(),
                applicationAttachmentDO.getStorageKey(),
                applicationAttachmentDO.getOriginalFilename(),
                applicationAttachmentDO.getContentType(),
                applicationAttachmentDO.getSize() == null ? 0L : applicationAttachmentDO.getSize(),
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

    private Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
