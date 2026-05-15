package edu.whut.eval.interfaces.student.request;

import jakarta.validation.constraints.NotNull;

/**
 * 提交申请请求。
 */
public class SubmitApplicationRequest {

    @NotNull
    private Long expectedVersion;

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
