package edu.whut.eval.application.platform.query;

public class PlatformMenuDeadline {

    private final String studentApplyDeadline;
    private final String finalSubmitDeadline;
    private final String source;

    public PlatformMenuDeadline(String studentApplyDeadline, String finalSubmitDeadline, String source) {
        this.studentApplyDeadline = studentApplyDeadline;
        this.finalSubmitDeadline = finalSubmitDeadline;
        this.source = source;
    }

    public String getStudentApplyDeadline() {
        return studentApplyDeadline;
    }

    public String getFinalSubmitDeadline() {
        return finalSubmitDeadline;
    }

    public String getSource() {
        return source;
    }
}
