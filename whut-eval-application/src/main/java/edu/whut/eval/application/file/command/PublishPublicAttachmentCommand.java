package edu.whut.eval.application.file.command;

public record PublishPublicAttachmentCommand(
        String fileId,
        String displayName,
        String description,
        String categoryCode,
        String scopeType,
        String scopeValue,
        Integer sortNo
) {
}
