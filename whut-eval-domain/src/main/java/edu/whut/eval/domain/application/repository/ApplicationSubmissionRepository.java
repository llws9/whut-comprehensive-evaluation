package edu.whut.eval.domain.application.repository;

import edu.whut.eval.domain.application.model.ApplicationSubmission;

import java.util.Optional;

/**
 * 申请提交聚合仓储。
 */
public interface ApplicationSubmissionRepository {

    /**
     * 按申请主键读取申请聚合。
     */
    Optional<ApplicationSubmission> findById(Long applicationId);

    /**
     * 保存申请聚合及其附件。
     */
    ApplicationSubmission save(ApplicationSubmission applicationSubmission);
}
