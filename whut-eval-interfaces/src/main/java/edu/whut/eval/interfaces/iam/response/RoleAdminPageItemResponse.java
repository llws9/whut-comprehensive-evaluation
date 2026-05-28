package edu.whut.eval.interfaces.iam.response;

public class RoleAdminPageItemResponse {

    private final Long roleId;
    private final String roleCode;
    private final String roleName;
    private final String roleScope;
    private final String status;
    private final int permissionCount;
    private final String createdAt;

    public RoleAdminPageItemResponse(Long roleId,
                                     String roleCode,
                                     String roleName,
                                     String roleScope,
                                     String status,
                                     int permissionCount,
                                     String createdAt) {
        this.roleId = roleId;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.roleScope = roleScope;
        this.status = status;
        this.permissionCount = permissionCount;
        this.createdAt = createdAt;
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

    public String getRoleScope() {
        return roleScope;
    }

    public String getStatus() {
        return status;
    }

    public int getPermissionCount() {
        return permissionCount;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
