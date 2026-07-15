package edu.whut.eval.application.platform.query;

import java.time.OffsetDateTime;

public class PlatformMenuDeadlineUpdateResult {

    private final String studentApplyDeadline;
    private final String finalSubmitDeadline;
    private final String timezone;
    private final OffsetDateTime effectiveAt;
    private final String source;

    public PlatformMenuDeadlineUpdateResult(String studentApplyDeadline,
                                            String finalSubmitDeadline,
                                            String timezone,
                                            OffsetDateTime effectiveAt,
                                            String source) {
        this.studentApplyDeadline = studentApplyDeadline;
        this.finalSubmitDeadline = finalSubmitDeadline;
        this.timezone = timezone;
        this.effectiveAt = effectiveAt;
        this.source = source;
    }

    public String getStudentApplyDeadline() {
        return studentApplyDeadline;
    }

    public String getFinalSubmitDeadline() {
        return finalSubmitDeadline;
    }

    public String getTimezone() {
        return timezone;
    }

    public OffsetDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public String getSource() {
        return source;
    }
}
