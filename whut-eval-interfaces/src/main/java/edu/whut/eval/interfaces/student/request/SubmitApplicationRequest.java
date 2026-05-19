package edu.whut.eval.interfaces.student.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 提交申请请求。
 */
public class SubmitApplicationRequest {

    @NotNull
    private Long expectedVersion;

    private BigDecimal appliedPoints;

    private String optionCode;

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public BigDecimal getAppliedPoints() {
        return appliedPoints;
    }

    public void setAppliedPoints(BigDecimal appliedPoints) {
        this.appliedPoints = appliedPoints;
    }

    public String getOptionCode() {
        return optionCode;
    }

    public void setOptionCode(String optionCode) {
        this.optionCode = optionCode;
    }
}
