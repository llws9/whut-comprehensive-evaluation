package edu.whut.eval.domain.finalrecord.service;

import java.time.Instant;

public interface FinalSubmissionWindowPolicy {

    void assertSubmitAllowed(long studentUserId, String academicYear, Instant now);
}
