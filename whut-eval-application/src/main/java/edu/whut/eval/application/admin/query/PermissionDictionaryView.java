package edu.whut.eval.application.admin.query;

public record PermissionDictionaryView(
        String permissionCode,
        String permissionName,
        String module,
        String description,
        String status
) {
}
