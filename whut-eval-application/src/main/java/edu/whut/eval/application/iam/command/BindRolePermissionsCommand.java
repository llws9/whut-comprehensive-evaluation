package edu.whut.eval.application.iam.command;

import java.util.List;

public record BindRolePermissionsCommand(
        Long roleId,
        List<String> permissionCodes,
        Boolean replaceAll
) {
}
