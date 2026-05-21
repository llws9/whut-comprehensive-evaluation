package edu.whut.eval.interfaces.iam.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class ReplaceRolePermissionsRequest {

    @NotNull
    private List<@NotBlank String> permissionCodes;

    private Boolean replaceAll;

    public List<String> getPermissionCodes() {
        return permissionCodes;
    }

    public void setPermissionCodes(List<String> permissionCodes) {
        this.permissionCodes = permissionCodes;
    }

    public Boolean getReplaceAll() {
        return replaceAll;
    }

    public void setReplaceAll(Boolean replaceAll) {
        this.replaceAll = replaceAll;
    }
}
