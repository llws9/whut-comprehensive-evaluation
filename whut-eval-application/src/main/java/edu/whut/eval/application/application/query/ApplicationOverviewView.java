package edu.whut.eval.application.application.query;

public record ApplicationOverviewView(long draftCount,
                                      long submittedCount,
                                      long returnedCount,
                                      long approvedCount,
                                      long rejectedCount,
                                      String latestAcademicYear) {
}
