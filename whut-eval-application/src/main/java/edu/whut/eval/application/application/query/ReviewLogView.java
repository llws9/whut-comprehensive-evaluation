package edu.whut.eval.application.application.query;

import java.time.Instant;

public record ReviewLogView(Long reviewLogId,
                            String action,
                            Long reviewerId,
                            String reviewerName,
                            String reviewRole,
                            String reason,
                            Instant reviewedAt) {
}
