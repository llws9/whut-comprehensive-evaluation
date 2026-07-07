package edu.whut.eval.application.application.command;

public record RejectReviewCommand(Long applicationId, Long expectedVersion, String reason) {
}
