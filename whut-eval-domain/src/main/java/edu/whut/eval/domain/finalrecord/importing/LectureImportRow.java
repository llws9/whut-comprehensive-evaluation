package edu.whut.eval.domain.finalrecord.importing;

public record LectureImportRow(
        Long rowNo,
        String studentNo,
        String scoreValue,
        String displayText
) {
}
