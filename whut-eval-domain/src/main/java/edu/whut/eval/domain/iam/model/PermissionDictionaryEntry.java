package edu.whut.eval.domain.iam.model;

public record PermissionDictionaryEntry(
        String permissionCode,
        String permissionName,
        String module,
        String description,
        String status
) {
}
