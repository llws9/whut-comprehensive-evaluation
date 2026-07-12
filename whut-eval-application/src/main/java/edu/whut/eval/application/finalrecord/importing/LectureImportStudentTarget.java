package edu.whut.eval.application.finalrecord.importing;

public record LectureImportStudentTarget(
        Long studentUserId,
        String studentNo,
        Long orgUnitId,
        String orgPath
) {
}
