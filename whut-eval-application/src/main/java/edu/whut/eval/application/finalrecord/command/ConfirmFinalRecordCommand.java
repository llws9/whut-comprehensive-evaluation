package edu.whut.eval.application.finalrecord.command;

public record ConfirmFinalRecordCommand(Long recordId, String comment, Long expectedVersion) {
}
