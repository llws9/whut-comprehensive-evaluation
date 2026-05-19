package edu.whut.eval.interfaces.iam.response;

/**
 * 角色分配响应。
 */
public class RoleAssignmentResponse {

    private final Long assignmentId;
    private final Long userId;
    private final String roleCode;
    private final String roleName;
    private final Long orgUnitId;
    private final String orgUnitName;
    private final String status;
    private final String effectiveFrom;
    private final String effectiveTo;
    private final String sourceType;
    private final String updatedAt;

    public RoleAssignmentResponse(Long assignmentId,
                                  Long userId,
                                  String roleCode,
                                  String roleName,
                                  Long orgUnitId,
                                  String orgUnitName,
                                  String status,
                                  String effectiveFrom,
                                  String effectiveTo,
                                  String sourceType,
                                  String updatedAt) {
        this.assignmentId = assignmentId;
        this.userId = userId;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.orgUnitId = orgUnitId;
        this.orgUnitName = orgUnitName;
        this.status = status;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.sourceType = sourceType;
        this.updatedAt = updatedAt;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public String getOrgUnitName() {
        return orgUnitName;
    }

    public String getStatus() {
        return status;
    }

    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getEffectiveTo() {
        return effectiveTo;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
