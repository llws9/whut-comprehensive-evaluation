package edu.whut.eval.application.application.command;

import java.util.List;

public record BatchApproveReviewCommand(List<Long> applicationIds, String comment) {
}
