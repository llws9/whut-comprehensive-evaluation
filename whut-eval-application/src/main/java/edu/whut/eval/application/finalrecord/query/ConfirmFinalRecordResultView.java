package edu.whut.eval.application.finalrecord.query;

import edu.whut.eval.domain.finalrecord.model.FinalRecordStatus;

import java.time.Instant;

public record ConfirmFinalRecordResultView(
        Long finalRecordId,
        FinalRecordStatus status,
        String confirmComment,
        Instant confirmedAt,
        Long version
) {
}
