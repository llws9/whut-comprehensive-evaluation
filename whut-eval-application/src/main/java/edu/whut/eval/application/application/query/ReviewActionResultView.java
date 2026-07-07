package edu.whut.eval.application.application.query;

import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;

import java.time.Instant;

public record ReviewActionResultView(Long applicationId,
                                     ApplicationSubmissionStatus status,
                                     Long version,
                                     Long reviewLogId,
                                     Instant reviewedAt) {
}
