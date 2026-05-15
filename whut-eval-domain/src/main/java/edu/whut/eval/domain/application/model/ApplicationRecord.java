package edu.whut.eval.domain.application.model;

/**
 * 申请查询结果的领域视图。
 * 当前只保留范围校验与列表展示必须的核心字段，避免在正式业务模型未落地前引入过多假设。
 */
public record ApplicationRecord(Long applicationId,
                                Long applicantUserId,
                                Long orgUnitId,
                                String orgPath,
                                String categoryCode,
                                String itemCode) {
}
