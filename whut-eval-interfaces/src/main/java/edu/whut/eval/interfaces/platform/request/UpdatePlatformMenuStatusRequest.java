package edu.whut.eval.interfaces.platform.request;

import jakarta.validation.constraints.NotBlank;

public class UpdatePlatformMenuStatusRequest {

    private Boolean studentApplyEnabled;
    private Boolean finalSubmitEnabled;

    @NotBlank(message = "reason 不能为空")
    private String reason;

    public Boolean getStudentApplyEnabled() {
        return studentApplyEnabled;
    }

    public void setStudentApplyEnabled(Boolean studentApplyEnabled) {
        this.studentApplyEnabled = studentApplyEnabled;
    }

    public Boolean getFinalSubmitEnabled() {
        return finalSubmitEnabled;
    }

    public void setFinalSubmitEnabled(Boolean finalSubmitEnabled) {
        this.finalSubmitEnabled = finalSubmitEnabled;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
