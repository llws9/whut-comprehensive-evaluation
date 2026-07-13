package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.domain.finalrecord.importing.ActivityImportRow;

import java.util.List;

public interface ActivityImportParser {
    List<ActivityImportRow> parse(byte[] fileContent);
}
