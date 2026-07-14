package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.domain.finalrecord.importing.MentorScoreImportRow;

import java.util.List;

public interface MentorScoreImportParser {
    List<MentorScoreImportRow> parse(byte[] fileContent);
}
