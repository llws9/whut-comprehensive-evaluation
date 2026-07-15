package edu.whut.eval.interfaces.platform.request;

import jakarta.validation.constraints.NotBlank;

public class ReplacePlatformDeadlineRequest {

    private String studentApplyDeadline;
    private String finalSubmitDeadline;

    @NotBlank(message = "reason 不能为空")
    private String reason;

    public String getStudentApplyDeadline() {
        return studentApplyDeadline;
    }

    public void setStudentApplyDeadline(String studentApplyDeadline) {
        this.studentApplyDeadline = studentApplyDeadline;
    }

    public String getFinalSubmitDeadline() {
        return finalSubmitDeadline;
    }

    public void setFinalSubmitDeadline(String finalSubmitDeadline) {
        this.finalSubmitDeadline = finalSubmitDeadline;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
