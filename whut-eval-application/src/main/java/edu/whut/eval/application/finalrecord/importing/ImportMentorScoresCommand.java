package edu.whut.eval.application.finalrecord.importing;

public record ImportMentorScoresCommand(
        byte[] fileContent,
        String academicYear,
        String importMode
) {
}
