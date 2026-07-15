package edu.whut.eval.application.platform.command;

public record ReplacePlatformDeadlineCommand(
        String studentApplyDeadline,
        String finalSubmitDeadline,
        String reason
) {
}
