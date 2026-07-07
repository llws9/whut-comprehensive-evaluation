package edu.whut.eval.domain.application.model;

import java.math.BigDecimal;

/**
 * 提交时评分快照。
 */
public record ApplicationScoringSnapshot(
        String optionCode,
        BigDecimal appliedPoints,
        BigDecimal maxPoints,
        int evidenceCount,
        boolean exceedsMaxPoints,
        String warningMessage
) {
}
