package edu.whut.eval.application.application.query;

import java.time.Instant;

public record ReviewApplicationListItemView(Long applicationId,
                                            Long applicantUserId,
                                            String applicantUserName,
                                            String applicantUserNo,
                                            Long orgUnitId,
                                            String orgUnitName,
                                            String categoryCode,
                                            String itemCode,
                                            String title,
                                            String status,
                                            Instant submittedAt,
                                            String currentReviewNode) {
}
