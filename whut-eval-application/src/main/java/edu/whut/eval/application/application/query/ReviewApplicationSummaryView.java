package edu.whut.eval.application.application.query;

import java.time.Instant;

public record ReviewApplicationSummaryView(Long applicationId,
                                           String status,
                                           String title,
                                           String description,
                                           String categoryCode,
                                           String itemCode,
                                           String academicYear,
                                           String term,
                                           Instant submittedAt,
                                           Long version,
                                           ReviewScoringSnapshotView scoringSnapshot) {
}
