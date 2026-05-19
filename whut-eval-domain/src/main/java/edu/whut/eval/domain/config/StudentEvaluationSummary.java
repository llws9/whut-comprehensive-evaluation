package edu.whut.eval.domain.config;

import java.math.BigDecimal;

public class StudentEvaluationSummary {

    private String studentId;
    private String studentName;
    private boolean isPartyMember;
    private int academicYear;
    private String grade;
    private BigDecimal moralScore = BigDecimal.ZERO;
    private BigDecimal intellectualScore = BigDecimal.ZERO;
    private BigDecimal sportsScore = BigDecimal.ZERO;
    private BigDecimal sportsCompetitionScore = BigDecimal.ZERO;
    private BigDecimal sportsArtContributionScore = BigDecimal.ZERO;
    private BigDecimal laborScore = BigDecimal.ZERO;
    private int failedCourseCount;
    private boolean hasMajorViolation;
    private int volunteerHours;

    public StudentEvaluationSummary() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public boolean isPartyMember() {
        return isPartyMember;
    }

    public void setPartyMember(boolean partyMember) {
        isPartyMember = partyMember;
    }

    public int getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(int academicYear) {
        this.academicYear = academicYear;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public BigDecimal getMoralScore() {
        return moralScore;
    }

    public void setMoralScore(BigDecimal moralScore) {
        this.moralScore = moralScore;
    }

    public BigDecimal getIntellectualScore() {
        return intellectualScore;
    }

    public void setIntellectualScore(BigDecimal intellectualScore) {
        this.intellectualScore = intellectualScore;
    }

    public BigDecimal getSportsScore() {
        return sportsScore;
    }

    public void setSportsScore(BigDecimal sportsScore) {
        this.sportsScore = sportsScore;
    }

    public BigDecimal getSportsCompetitionScore() {
        return sportsCompetitionScore;
    }

    public void setSportsCompetitionScore(BigDecimal sportsCompetitionScore) {
        this.sportsCompetitionScore = sportsCompetitionScore;
    }

    public BigDecimal getSportsArtContributionScore() {
        return sportsArtContributionScore;
    }

    public void setSportsArtContributionScore(BigDecimal sportsArtContributionScore) {
        this.sportsArtContributionScore = sportsArtContributionScore;
    }

    public BigDecimal getLaborScore() {
        return laborScore;
    }

    public void setLaborScore(BigDecimal laborScore) {
        this.laborScore = laborScore;
    }

    public int getFailedCourseCount() {
        return failedCourseCount;
    }

    public void setFailedCourseCount(int failedCourseCount) {
        this.failedCourseCount = failedCourseCount;
    }

    public boolean isHasMajorViolation() {
        return hasMajorViolation;
    }

    public void setHasMajorViolation(boolean hasMajorViolation) {
        this.hasMajorViolation = hasMajorViolation;
    }

    public int getVolunteerHours() {
        return volunteerHours;
    }

    public void setVolunteerHours(int volunteerHours) {
        this.volunteerHours = volunteerHours;
    }

    public static class Builder {
        private final StudentEvaluationSummary summary = new StudentEvaluationSummary();

        public Builder studentId(String studentId) {
            summary.studentId = studentId;
            return this;
        }

        public Builder studentName(String studentName) {
            summary.studentName = studentName;
            return this;
        }

        public Builder partyMember(boolean isPartyMember) {
            summary.isPartyMember = isPartyMember;
            return this;
        }

        public Builder academicYear(int academicYear) {
            summary.academicYear = academicYear;
            return this;
        }

        public Builder grade(String grade) {
            summary.grade = grade;
            return this;
        }

        public Builder moralScore(BigDecimal moralScore) {
            summary.moralScore = moralScore;
            return this;
        }

        public Builder intellectualScore(BigDecimal intellectualScore) {
            summary.intellectualScore = intellectualScore;
            return this;
        }

        public Builder sportsScore(BigDecimal sportsScore) {
            summary.sportsScore = sportsScore;
            return this;
        }

        public Builder sportsCompetitionScore(BigDecimal sportsCompetitionScore) {
            summary.sportsCompetitionScore = sportsCompetitionScore;
            return this;
        }

        public Builder sportsArtContributionScore(BigDecimal sportsArtContributionScore) {
            summary.sportsArtContributionScore = sportsArtContributionScore;
            return this;
        }

        public Builder laborScore(BigDecimal laborScore) {
            summary.laborScore = laborScore;
            return this;
        }

        public Builder failedCourseCount(int failedCourseCount) {
            summary.failedCourseCount = failedCourseCount;
            return this;
        }

        public Builder hasMajorViolation(boolean hasMajorViolation) {
            summary.hasMajorViolation = hasMajorViolation;
            return this;
        }

        public Builder volunteerHours(int volunteerHours) {
            summary.volunteerHours = volunteerHours;
            return this;
        }

        public StudentEvaluationSummary build() {
            return summary;
        }
    }
}
