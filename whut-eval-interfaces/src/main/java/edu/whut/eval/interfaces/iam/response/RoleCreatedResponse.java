package edu.whut.eval.interfaces.iam.response;

public class RoleCreatedResponse {

    private final Long roleId;
    private final String roleCode;
    private final String roleName;
    private final String status;

    public RoleCreatedResponse(Long roleId, String roleCode, String roleName, String status) {
        this.roleId = roleId;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.status = status;
    }

    public Long getRoleId() {
        return roleId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getStatus() {
        return status;
    }
}
