package edu.whut.eval.application.platform.query;

import java.time.OffsetDateTime;

public class PlatformMenuStatusUpdateResult {

    private final boolean studentApplyEnabled;
    private final boolean finalSubmitEnabled;
    private final OffsetDateTime effectiveAt;
    private final String source;

    public PlatformMenuStatusUpdateResult(boolean studentApplyEnabled,
                                          boolean finalSubmitEnabled,
                                          OffsetDateTime effectiveAt,
                                          String source) {
        this.studentApplyEnabled = studentApplyEnabled;
        this.finalSubmitEnabled = finalSubmitEnabled;
        this.effectiveAt = effectiveAt;
        this.source = source;
    }

    public boolean isStudentApplyEnabled() {
        return studentApplyEnabled;
    }

    public boolean isFinalSubmitEnabled() {
        return finalSubmitEnabled;
    }

    public OffsetDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public String getSource() {
        return source;
    }
}
