package edu.whut.eval.interfaces.iam.request;

import jakarta.validation.constraints.NotBlank;

public class CreateRoleRequest {

    @NotBlank
    private String roleCode;

    @NotBlank
    private String roleName;

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}
