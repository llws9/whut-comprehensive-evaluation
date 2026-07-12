package edu.whut.eval.application.finalrecord.importing;

public record ImportLecturesCommand(
        byte[] fileContent,
        String title,
        String heldAt,
        String academicYear
) {
}
