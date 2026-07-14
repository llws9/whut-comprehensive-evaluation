package edu.whut.eval.application.finalrecord.importing;

public record MentorScoreImportStudentTarget(
        Long studentUserId,
        String studentNo,
        Long orgUnitId,
        String orgPath,
        String finalRecordStatus
) {
}
