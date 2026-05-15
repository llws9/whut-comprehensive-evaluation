package edu.whut.eval.application.application.command;

/**
 * 提交申请命令。
 */
public class SubmitApplicationCommand {

    private final Long applicationId;
    private final Long expectedVersion;

    public SubmitApplicationCommand(Long applicationId, Long expectedVersion) {
        this.applicationId = applicationId;
        this.expectedVersion = expectedVersion;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }
}
