package edu.whut.eval.application.application.command;

import java.math.BigDecimal;

/**
 * 提交申请命令。
 */
public class SubmitApplicationCommand {

    private final Long applicationId;
    private final Long expectedVersion;
    private final BigDecimal appliedPoints;
    private final String optionCode;

    public SubmitApplicationCommand(Long applicationId, Long expectedVersion) {
        this(applicationId, expectedVersion, null, null);
    }

    public SubmitApplicationCommand(Long applicationId, Long expectedVersion, BigDecimal appliedPoints, String optionCode) {
        this.applicationId = applicationId;
        this.expectedVersion = expectedVersion;
        this.appliedPoints = appliedPoints;
        this.optionCode = optionCode;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public BigDecimal getAppliedPoints() {
        return appliedPoints;
    }

    public String getOptionCode() {
        return optionCode;
    }
}
