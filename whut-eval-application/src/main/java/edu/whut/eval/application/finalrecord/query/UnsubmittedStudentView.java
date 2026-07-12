package edu.whut.eval.application.finalrecord.query;

public record UnsubmittedStudentView(
        Long studentUserId,
        String userNo,
        String userName,
        String grade,
        String className,
        String status,
        String lastUpdatedAt
) {
}
