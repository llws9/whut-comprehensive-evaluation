package edu.whut.eval.application.application.command;

public record ApproveReviewCommand(Long applicationId, Long expectedVersion, String comment) {
}
