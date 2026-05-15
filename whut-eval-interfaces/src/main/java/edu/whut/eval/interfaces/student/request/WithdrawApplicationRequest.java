package edu.whut.eval.interfaces.student.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 撤回申请请求。
 */
public class WithdrawApplicationRequest {

    @NotBlank
    private String reason;

    @NotNull
    private Long expectedVersion;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
