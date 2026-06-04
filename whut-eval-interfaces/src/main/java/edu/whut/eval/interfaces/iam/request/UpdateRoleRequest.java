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
    private String expectedRoleName;
    @NotBlank
    private String expectedRoleScope;
    @NotBlank
    private String expectedStatus;

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getRoleScope() { return roleScope; }
    public void setRoleScope(String roleScope) { this.roleScope = roleScope; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExpectedRoleName() { return expectedRoleName; }
    public void setExpectedRoleName(String expectedRoleName) { this.expectedRoleName = expectedRoleName; }
    public String getExpectedRoleScope() { return expectedRoleScope; }
    public void setExpectedRoleScope(String expectedRoleScope) { this.expectedRoleScope = expectedRoleScope; }
    public String getExpectedStatus() { return expectedStatus; }
    public void setExpectedStatus(String expectedStatus) { this.expectedStatus = expectedStatus; }
}
