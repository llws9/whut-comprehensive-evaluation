package edu.whut.eval.interfaces.iam.request;

import jakarta.validation.constraints.NotBlank;

public class UpdateUserStatusRequest {

    @NotBlank
    private String status;

    private String reason;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
