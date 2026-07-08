package edu.whut.eval.domain.finalrecord.service;

import java.time.Instant;

public class NoopFinalSubmissionWindowPolicy implements FinalSubmissionWindowPolicy {

    @Override
    public void assertSubmitAllowed(long studentUserId, String academicYear, Instant now) {
        // Minimal D only installs the extension point; platform-window enforcement is deferred.
    }
}
