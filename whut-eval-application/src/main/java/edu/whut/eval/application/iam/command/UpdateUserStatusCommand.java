package edu.whut.eval.application.iam.command;

public record UpdateUserStatusCommand(String status, String reason) {
}