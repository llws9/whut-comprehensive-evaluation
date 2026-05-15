package edu.whut.eval.application.application.command;

/**
 * 撤回申请命令。
 */
public class WithdrawApplicationCommand {

    private final Long applicationId;
    private final String reason;
    private final Long expectedVersion;

    public WithdrawApplicationCommand(Long applicationId, String reason, Long expectedVersion) {
        this.applicationId = applicationId;
        this.reason = reason;
        this.expectedVersion = expectedVersion;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public String getReason() {
        return reason;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }
}
