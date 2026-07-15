package edu.whut.eval.interfaces.file.request;

import jakarta.validation.constraints.NotBlank;

public class OfflinePublicAttachmentRequest {

    @NotBlank
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
