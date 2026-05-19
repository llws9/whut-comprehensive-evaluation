package edu.whut.eval.interfaces.iam.request;

import jakarta.validation.constraints.Positive;

/**
 * 修改角色分配请求。
 */
public class UpdateRoleAssignmentRequest {

    private String status;

    @Positive
    private Long orgUnitId;

    private String effectiveFrom;

    private String effectiveTo;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(Long orgUnitId) {
        this.orgUnitId = orgUnitId;
    }

    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(String effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public String getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(String effectiveTo) {
        this.effectiveTo = effectiveTo;
    }
}
