package edu.whut.eval.application.application.query;

public record ReviewTaskSummaryView(long pendingCount,
                                    long approvedToday,
                                    long returnedToday,
                                    long rejectedToday,
                                    long processedToday) {
}
