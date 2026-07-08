package edu.whut.eval.domain.finalrecord.repository;

import edu.whut.eval.domain.finalrecord.model.FinalComponentScore;

import java.math.BigDecimal;
import java.util.List;

public record AggregatedFinalRecordSnapshot(
        BigDecimal moralTotal,
        BigDecimal intellectualTotal,
        BigDecimal physicalTotal,
        BigDecimal laborTotal,
        BigDecimal grandTotal,
        List<FinalComponentScore> components
) {
}
