package edu.whut.eval.application.application.query;

import java.util.List;

public record ReviewApplicationDetailView(ReviewApplicationSummaryView application,
                                          ReviewApplicantView applicant,
                                          List<ApplicationAttachmentView> attachments,
                                          List<ReviewLogView> reviewLogs,
                                          List<String> allowedActions) {
}
