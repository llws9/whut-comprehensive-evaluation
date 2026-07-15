package edu.whut.eval.application.platform.command;

public record UpdatePlatformMenuStatusCommand(
        Boolean studentApplyEnabled,
        Boolean finalSubmitEnabled,
        String reason
) {
}
