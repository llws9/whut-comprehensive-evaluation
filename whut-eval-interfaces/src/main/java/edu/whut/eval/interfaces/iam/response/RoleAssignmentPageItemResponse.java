package edu.whut.eval.interfaces.iam.response;

/**
 * 角色分配分页行响应。
 */
public class RoleAssignmentPageItemResponse {

    private final Long assignmentId;
    private final Long userId;
    private final String userNo;
    private final String userName;
    private final String roleCode;
    private final String roleName;
    private final Long orgUnitId;
    private final String orgUnitName;
    private final String status;
    private final String effectiveFrom;
    private final String effectiveTo;

    public RoleAssignmentPageItemResponse(Long assignmentId,
                                          Long userId,
                                          String userNo,
                                          String userName,
                                          String roleCode,
                                          String roleName,
                                          Long orgUnitId,
                                          String orgUnitName,
                                          String status,
                                          String effectiveFrom,
                                          String effectiveTo) {
        this.assignmentId = assignmentId;
        this.userId = userId;
        this.userNo = userNo;
        this.userName = userName;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.orgUnitId = orgUnitId;
        this.orgUnitName = orgUnitName;
        this.status = status;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserNo() {
        return userNo;
    }

    public String getUserName() {
        return userName;
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
}
