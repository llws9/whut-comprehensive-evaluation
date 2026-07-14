package edu.whut.eval.application.finalrecord.exporting;

import java.util.List;

public interface FinalScoreExportWorkbookWriter {
    FinalScoreExportFile write(String academicYear, List<FinalScoreExportRow> rows);
}
