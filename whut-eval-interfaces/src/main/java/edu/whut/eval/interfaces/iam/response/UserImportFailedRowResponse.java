package edu.whut.eval.interfaces.iam.response;

public record UserImportFailedRowResponse(
        long rowNo,
        String userNo,
        String reason
) {
}
