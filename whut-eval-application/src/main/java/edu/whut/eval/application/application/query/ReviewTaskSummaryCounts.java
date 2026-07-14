package edu.whut.eval.application.application.query;

public record ReviewTaskSummaryCounts(long pendingCount,
                                      long approvedToday,
                                      long returnedToday,
                                      long rejectedToday) {
}
