package edu.whut.eval.interfaces.config;

import edu.whut.eval.application.config.EvaluationConfigApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.domain.config.StudentContext;
import edu.whut.eval.domain.config.StudentEvaluationSummary;
import edu.whut.eval.domain.config.model.EligibilityRulesConfig;
import edu.whut.eval.domain.config.model.EvaluationItemsConfig;
import edu.whut.eval.domain.config.model.IndexOptionsConfig;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config/evaluation")
public class EvaluationConfigController {

    private final EvaluationConfigApplicationService evaluationConfigApplicationService;

    public EvaluationConfigController(EvaluationConfigApplicationService evaluationConfigApplicationService) {
        this.evaluationConfigApplicationService = evaluationConfigApplicationService;
    }

    @GetMapping("/items/{categoryCode}")
    public ApiResponse<List<EvaluationItemsConfig.EvaluationItem>> getItemsByCategory(
            @PathVariable String categoryCode) {
        return ApiResponse.success(evaluationConfigApplicationService.getItemsByCategory(categoryCode));
    }

    @GetMapping("/items/detail/{itemCode}")
    public ApiResponse<EvaluationItemsConfig.EvaluationItem> getEvaluationItem(
            @PathVariable String itemCode) {
        return ApiResponse.success(evaluationConfigApplicationService.getEvaluationItem(itemCode));
    }

    @GetMapping("/options/{itemCode}")
    public ApiResponse<List<IndexOptionsConfig.OptionItem>> getOptionsByItemCode(
            @PathVariable String itemCode) {
        return ApiResponse.success(evaluationConfigApplicationService.getOptionsByItemCode(itemCode));
    }

    @PostMapping("/calculate/points")
    public ApiResponse<BigDecimal> calculatePoints(@RequestBody PointsCalculationRequest request) {
        StudentContext context = StudentContext.builder()
                .studentId(request.getStudentId())
                .grade(request.getGrade())
                .academicYear(request.getAcademicYear())
                .partyMember(request.isPartyMember())
                .build();
        BigDecimal points = evaluationConfigApplicationService.calculatePoints(
                request.getItemCode(), request.getOptionCode(), context);
        return ApiResponse.success(points);
    }

    @PostMapping("/calculate/max-points")
    public ApiResponse<BigDecimal> calculateMaxPoints(@RequestBody MaxPointsCalculationRequest request) {
        StudentContext context = StudentContext.builder()
                .studentId(request.getStudentId())
                .grade(request.getGrade())
                .academicYear(request.getAcademicYear())
                .partyMember(request.isPartyMember())
                .build();
        BigDecimal maxPoints = evaluationConfigApplicationService.calculateMaxPoints(
                request.getItemCode(), context);
        return ApiResponse.success(maxPoints);
    }

    @GetMapping("/allows-custom-points/{itemCode}/{optionCode}")
    public ApiResponse<Boolean> allowsCustomPoints(
            @PathVariable String itemCode,
            @PathVariable String optionCode) {
        return ApiResponse.success(evaluationConfigApplicationService.allowsCustomPoints(itemCode, optionCode));
    }

    @PostMapping("/evaluate-eligibility/{categoryCode}")
    public ApiResponse<Boolean> evaluateEligibility(
            @PathVariable String categoryCode,
            @RequestBody EligibilityEvaluationRequest request) {
        StudentEvaluationSummary summary = StudentEvaluationSummary.builder()
                .studentId(request.getStudentId())
                .studentName(request.getStudentName())
                .partyMember(request.isPartyMember())
                .academicYear(request.getAcademicYear())
                .grade(request.getGrade())
                .moralScore(request.getMoralScore())
                .intellectualScore(request.getIntellectualScore())
                .sportsScore(request.getSportsScore())
                .sportsCompetitionScore(request.getSportsCompetitionScore())
                .sportsArtContributionScore(request.getSportsArtContributionScore())
                .laborScore(request.getLaborScore())
                .failedCourseCount(request.getFailedCourseCount())
                .hasMajorViolation(request.isHasMajorViolation())
                .volunteerHours(request.getVolunteerHours())
                .build();
        boolean eligible = evaluationConfigApplicationService.evaluateEligibility(categoryCode, summary);
        return ApiResponse.success(eligible);
    }

    @GetMapping("/eligibility-rules")
    public ApiResponse<Map<String, List<EligibilityRulesConfig.EligibilityRuleItem>>> getAllEligibilityRules() {
        return ApiResponse.success(evaluationConfigApplicationService.getAllEligibilityRules());
    }

    public static class PointsCalculationRequest {
        private String itemCode;
        private String optionCode;
        private String studentId;
        private String grade;
        private int academicYear;
        private boolean partyMember;

        public String getItemCode() {
            return itemCode;
        }

        public void setItemCode(String itemCode) {
            this.itemCode = itemCode;
        }

        public String getOptionCode() {
            return optionCode;
        }

        public void setOptionCode(String optionCode) {
            this.optionCode = optionCode;
        }

        public String getStudentId() {
            return studentId;
        }

        public void setStudentId(String studentId) {
            this.studentId = studentId;
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

        public boolean isPartyMember() {
            return partyMember;
        }

        public void setPartyMember(boolean partyMember) {
            this.partyMember = partyMember;
        }
    }

    public static class MaxPointsCalculationRequest {
        private String itemCode;
        private String studentId;
        private String grade;
        private int academicYear;
        private boolean partyMember;

        public String getItemCode() {
            return itemCode;
        }

        public void setItemCode(String itemCode) {
            this.itemCode = itemCode;
        }

        public String getStudentId() {
            return studentId;
        }

        public void setStudentId(String studentId) {
            this.studentId = studentId;
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

        public boolean isPartyMember() {
            return partyMember;
        }

        public void setPartyMember(boolean partyMember) {
            this.partyMember = partyMember;
        }
    }

    public static class EligibilityEvaluationRequest {
        private String studentId;
        private String studentName;
        private boolean partyMember;
        private int academicYear;
        private String grade;
        private BigDecimal moralScore;
        private BigDecimal intellectualScore;
        private BigDecimal sportsScore;
        private BigDecimal sportsCompetitionScore;
        private BigDecimal sportsArtContributionScore;
        private BigDecimal laborScore;
        private int failedCourseCount;
        private boolean hasMajorViolation;
        private int volunteerHours;

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
            return partyMember;
        }

        public void setPartyMember(boolean partyMember) {
            this.partyMember = partyMember;
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
    }
}
