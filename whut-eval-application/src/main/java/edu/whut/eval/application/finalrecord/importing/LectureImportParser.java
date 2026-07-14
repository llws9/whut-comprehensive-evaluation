package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.domain.finalrecord.importing.LectureImportRow;

import java.util.List;

public interface LectureImportParser {
    List<LectureImportRow> parse(byte[] fileContent);
}
