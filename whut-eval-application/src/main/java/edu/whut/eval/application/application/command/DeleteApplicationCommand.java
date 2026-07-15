package edu.whut.eval.application.application.command;

public record DeleteApplicationCommand(Long applicationId, Long expectedVersion) {
}
