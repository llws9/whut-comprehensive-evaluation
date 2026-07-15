package edu.whut.eval.interfaces.student.request;

import jakarta.validation.constraints.NotNull;

public class DeleteApplicationRequest {

    @NotNull(message = "expectedVersion 不能为空")
    private Long expectedVersion;

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
