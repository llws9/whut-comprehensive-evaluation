package edu.whut.eval.application.iam.command;

import java.util.List;

public record ReplaceRolePermissionsCommand(List<String> permissionCodes, Boolean replaceAll) {
}
