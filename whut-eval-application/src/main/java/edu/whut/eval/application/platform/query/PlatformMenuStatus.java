package edu.whut.eval.application.platform.query;

public class PlatformMenuStatus {

    private final boolean studentApplyEnabled;
    private final boolean finalSubmitEnabled;
    private final String source;

    public PlatformMenuStatus(boolean studentApplyEnabled, boolean finalSubmitEnabled, String source) {
        this.studentApplyEnabled = studentApplyEnabled;
        this.finalSubmitEnabled = finalSubmitEnabled;
        this.source = source;
    }

    public boolean isStudentApplyEnabled() {
        return studentApplyEnabled;
    }

    public boolean isFinalSubmitEnabled() {
        return finalSubmitEnabled;
    }

    public String getSource() {
        return source;
    }
}
