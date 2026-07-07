package edu.whut.eval.application.application.command;

public record ReturnReviewCommand(Long applicationId, Long expectedVersion, String reason) {
}
