package edu.whut.eval.infra.persistence.repository.row;

public class PermissionDictionaryRow {

    private String permissionCode;
    private String permissionName;
    private String module;
    private String description;
    private String status;

    public PermissionDictionaryRow() {
    }

    public PermissionDictionaryRow(String permissionCode,
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

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    public String getPermissionName() {
        return permissionName;
    }

    public void setPermissionName(String permissionName) {
        this.permissionName = permissionName;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
