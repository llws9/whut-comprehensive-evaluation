package edu.whut.eval.application.iam.command;

public record ImportUsersCommand(
        byte[] fileContent,
        String importMode
) {
}
