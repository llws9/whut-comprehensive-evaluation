package edu.whut.eval.domain.config;

import java.math.BigDecimal;
import java.util.Map;

public class StudentContext {

    private String studentId;
    private String studentName;
    private String grade;
    private int academicYear;
    private String className;
    private String major;
    private boolean isPartyMember;
    private Map<String, Object> customAttributes;

    public StudentContext() {
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

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public int getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(int academicYear) {
        this.academicYear = academicYear;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public boolean isPartyMember() {
        return isPartyMember;
    }

    public void setPartyMember(boolean partyMember) {
        isPartyMember = partyMember;
    }

    public Map<String, Object> getCustomAttributes() {
        return customAttributes;
    }

    public void setCustomAttributes(Map<String, Object> customAttributes) {
        this.customAttributes = customAttributes;
    }

    public static class Builder {
        private final StudentContext context = new StudentContext();

        public Builder studentId(String studentId) {
            context.studentId = studentId;
            return this;
        }

        public Builder studentName(String studentName) {
            context.studentName = studentName;
            return this;
        }

        public Builder grade(String grade) {
            context.grade = grade;
            return this;
        }

        public Builder academicYear(int academicYear) {
            context.academicYear = academicYear;
            return this;
        }

        public Builder className(String className) {
            context.className = className;
            return this;
        }

        public Builder major(String major) {
            context.major = major;
            return this;
        }

        public Builder partyMember(boolean isPartyMember) {
            context.isPartyMember = isPartyMember;
            return this;
        }

        public Builder customAttributes(Map<String, Object> customAttributes) {
            context.customAttributes = customAttributes;
            return this;
        }

        public StudentContext build() {
            return context;
        }
    }
}