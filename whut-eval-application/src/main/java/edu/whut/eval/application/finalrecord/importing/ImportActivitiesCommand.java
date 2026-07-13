package edu.whut.eval.application.finalrecord.importing;

public record ImportActivitiesCommand(
        byte[] fileContent,
        String title,
        String itemCode,
        String scoreValue,
        String heldAt,
        String academicYear
) {
}
