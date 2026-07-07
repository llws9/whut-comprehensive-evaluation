package edu.whut.eval.application.application.query;

import java.math.BigDecimal;

public record ReviewScoringSnapshotView(String optionCode,
                                        BigDecimal appliedPoints,
                                        BigDecimal maxPoints,
                                        int evidenceCount,
                                        boolean exceedsMaxPoints,
                                        String warningMessage) {
}
