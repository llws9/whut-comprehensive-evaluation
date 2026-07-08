package edu.whut.eval.interfaces.admin.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ConfirmFinalRecordRequest {
    @Size(max = 1000)
    private String comment;
    @NotNull
    private Long expectedVersion;

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
