package edu.whut.eval.application.iam.query;

public record UserImportFailedRowView(
        long rowNo,
        String userNo,
        String reason
) {
}
