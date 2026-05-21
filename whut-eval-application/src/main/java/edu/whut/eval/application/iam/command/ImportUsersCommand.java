package edu.whut.eval.application.iam.command;

import java.io.InputStream;

public record ImportUsersCommand(
        InputStream inputStream,
        String originalFilename,
        long size,
        String importMode
) {
}
