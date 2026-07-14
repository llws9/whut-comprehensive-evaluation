package edu.whut.eval.domain.finalrecord.importing;

public record MentorScoreImportRow(
        Long rowNo,
        String studentNo,
        String categoryCode,
        String itemCode,
        String scoreValue,
        String displayText,
        String sourceRefId
) {
}
