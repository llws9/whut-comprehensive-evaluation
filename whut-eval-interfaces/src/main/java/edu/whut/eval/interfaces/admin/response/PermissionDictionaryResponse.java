package edu.whut.eval.interfaces.admin.response;

public class PermissionDictionaryResponse {

    private final String permissionCode;
    private final String permissionName;
    private final String module;
    private final String description;
    private final String status;

    public PermissionDictionaryResponse(String permissionCode,
                                        String permissionName,
                                        String module,
                                        String description,
                                        String status) {
        this.permissionCode = permissionCode;
        this.permissionName = permissionName;
        this.module = module;
        this.description = description;
        this.status = status;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public String getPermissionName() {
        return permissionName;
    }

    public String getModule() {
        return module;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }
}
