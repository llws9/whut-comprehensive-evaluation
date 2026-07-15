package edu.whut.eval.application.file.command;

public record OfflinePublicAttachmentCommand(
        Long entryId,
        String reason
) {
}
