package edu.whut.eval.domain.score.model;

/**
 * 成绩查询结果的领域视图。
 * 当前保留范围校验与列表展示所需的核心字段，方便与申请查询链路保持对齐。
 */
public record ScoreRecord(Long scoreId,
                          Long studentUserId,
                          Long orgUnitId,
                          String orgPath,
                          String categoryCode,
                          String itemCode,
                          String academicYear) {
}
