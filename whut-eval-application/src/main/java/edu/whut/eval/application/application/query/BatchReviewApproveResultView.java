package edu.whut.eval.application.application.query;

import java.time.Instant;
import java.util.List;

public record BatchReviewApproveResultView(long totalCount,
                                           long successCount,
                                           long failedCount,
                                           List<BatchReviewApproveFailedItemView> failedItems,
                                           Instant processedAt) {
}
