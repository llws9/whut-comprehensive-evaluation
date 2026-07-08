package edu.whut.eval.application.finalrecord.query;

import java.util.List;

public record AdminFinalRecordDetailView(
        FinalRecordView record,
        FinalRecordStudentView student,
        List<FinalComponentScoreView> components
) {
}
