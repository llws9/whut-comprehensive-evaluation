package edu.whut.eval.interfaces.iam.request;

import jakarta.validation.constraints.NotBlank;

public class UpdateRoleRequest {

    @NotBlank
    private String roleName;

    @NotBlank
    private String roleScope;

    @NotBlank
    private String status;

    @NotBlank
    private String snapshotRoleName;

    @NotBlank
    private String snapshotRoleScope;

    @NotBlank
    private String snapshotStatus;

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleScope() {
        return roleScope;
    }

    public void setRoleScope(String roleScope) {
        this.roleScope = roleScope;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSnapshotRoleName() {
        return snapshotRoleName;
    }

    public void setSnapshotRoleName(String snapshotRoleName) {
        this.snapshotRoleName = snapshotRoleName;
    }

    public String getSnapshotRoleScope() {
        return snapshotRoleScope;
    }

    public void setSnapshotRoleScope(String snapshotRoleScope) {
        this.snapshotRoleScope = snapshotRoleScope;
    }

    public String getSnapshotStatus() {
        return snapshotStatus;
    }

    public void setSnapshotStatus(String snapshotStatus) {
        this.snapshotStatus = snapshotStatus;
    }
}
