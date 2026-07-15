package edu.whut.eval.domain.application.query;

public record ApplicationOverviewSummary(long draftCount,
                                         long submittedCount,
                                         long returnedCount,
                                         long approvedCount,
                                         long rejectedCount,
                                         String latestAcademicYear) {
}
