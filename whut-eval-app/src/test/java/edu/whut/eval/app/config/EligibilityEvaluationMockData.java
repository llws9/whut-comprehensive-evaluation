package edu.whut.eval.app.config;

import edu.whut.eval.domain.config.StudentEvaluationSummary;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class EligibilityEvaluationMockData {

    public static List<StudentEvaluationSummary> getMockStudents() {
        List<StudentEvaluationSummary> students = new ArrayList<>();

        // ============ 党员学生测试用例 ============

        // 党员学生 - 满足劳育条件（1.5分及以上）
        students.add(StudentEvaluationSummary.builder()
                .studentId("2023001")
                .studentName("张三（党员-满足）")
                .partyMember(true)
                .academicYear(2023)
                .grade("硕士")
                .moralScore(new BigDecimal("9.5"))
                .intellectualScore(new BigDecimal("85.0"))
                .sportsScore(new BigDecimal("3.0"))
                .laborScore(new BigDecimal("2.0"))     // 党员需1.5分，已满足
                .failedCourseCount(0)
                .hasMajorViolation(false)
                .volunteerHours(10)                   // 党员需8小时志愿，已满足
                .build());

        // 党员学生 - 不满足劳育条件（低于1.5分）
        students.add(StudentEvaluationSummary.builder()
                .studentId("2023002")
                .studentName("李四（党员-不满足）")
                .partyMember(true)
                .academicYear(2023)
                .grade("硕士")
                .moralScore(new BigDecimal("8.0"))
                .intellectualScore(new BigDecimal("70.0"))
                .sportsScore(new BigDecimal("2.5"))
                .laborScore(new BigDecimal("1.0"))     // 党员需1.5分，未满足
                .failedCourseCount(0)
                .hasMajorViolation(false)
                .volunteerHours(5)                    // 党员需8小时志愿，未满足
                .build());

        // 党员学生 - 志愿服务未满足
        students.add(StudentEvaluationSummary.builder()
                .studentId("2023003")
                .studentName("王五（党员-志愿不足）")
                .partyMember(true)
                .academicYear(2023)
                .grade("博士")
                .moralScore(new BigDecimal("9.0"))
                .intellectualScore(new BigDecimal("90.0"))
                .sportsScore(new BigDecimal("4.0"))
                .laborScore(new BigDecimal("3.0"))     // 劳育分数满足
                .failedCourseCount(0)
                .hasMajorViolation(false)
                .volunteerHours(5)                    // 党员需8小时志愿，未满足
                .build());

        // ============ 非党员学生测试用例 ============

        // 非党员学生 - 满足劳育条件（1分及以上）
        students.add(StudentEvaluationSummary.builder()
                .studentId("2023004")
                .studentName("赵六（非党员-满足）")
                .partyMember(false)
                .academicYear(2023)
                .grade("硕士")
                .moralScore(new BigDecimal("8.5"))
                .intellectualScore(new BigDecimal("75.0"))
                .sportsScore(new BigDecimal("2.0"))
                .laborScore(new BigDecimal("1.5"))     // 非党员需1分，已满足
                .failedCourseCount(0)
                .hasMajorViolation(false)
                .volunteerHours(0)                    // 非党员无志愿要求
                .build());

        // 非党员学生 - 不满足劳育条件（低于1分）
        students.add(StudentEvaluationSummary.builder()
                .studentId("2023005")
                .studentName("钱七（非党员-不满足）")
                .partyMember(false)
                .academicYear(2023)
                .grade("硕士")
                .moralScore(new BigDecimal("7.5"))
                .intellectualScore(new BigDecimal("65.0"))
                .sportsScore(new BigDecimal("1.5"))
                .laborScore(new BigDecimal("0.8"))     // 非党员需1分，未满足
                .failedCourseCount(0)
                .hasMajorViolation(false)
                .volunteerHours(0)
                .build());

        // ============ 边界条件测试用例 ============

        // 党员学生 - 刚好满足1.5分
        students.add(StudentEvaluationSummary.builder()
                .studentId("2023006")
                .studentName("孙八（党员-边界1.5）")
                .partyMember(true)
                .academicYear(2023)
                .grade("硕士")
                .moralScore(new BigDecimal("8.0"))
                .intellectualScore(new BigDecimal("72.0"))
                .sportsScore(new BigDecimal("2.0"))
                .laborScore(new BigDecimal("1.5"))     // 刚好满足1.5分
                .failedCourseCount(0)
                .hasMajorViolation(false)
                .volunteerHours(8)                    // 刚好满足8小时
                .build());

        // 非党员学生 - 刚好满足1分
        students.add(StudentEvaluationSummary.builder()
                .studentId("2023007")
                .studentName("周九（非党员-边界1.0）")
                .partyMember(false)
                .academicYear(2023)
                .grade("硕士")
                .moralScore(new BigDecimal("8.0"))
                .intellectualScore(new BigDecimal("70.0"))
                .sportsScore(new BigDecimal("2.0"))
                .laborScore(new BigDecimal("1.0"))     // 刚好满足1分
                .failedCourseCount(0)
                .hasMajorViolation(false)
                .volunteerHours(0)
                .build());

        // ============ 其他类别测试用例 ============

        // 德育不满足（低于8分）
        students.add(StudentEvaluationSummary.builder()
                .studentId("2023008")
                .studentName("吴十（德育不足）")
                .partyMember(false)
                .academicYear(2023)
                .grade("硕士")
                .moralScore(new BigDecimal("7.0"))     // 德育需8分，未满足
                .intellectualScore(new BigDecimal("80.0"))
                .sportsScore(new BigDecimal("3.0"))
                .laborScore(new BigDecimal("2.0"))
                .failedCourseCount(0)
                .hasMajorViolation(false)
                .volunteerHours(0)
                .build());

        // 智育不满足（有不及格课程）
        students.add(StudentEvaluationSummary.builder()
                .studentId("2023009")
                .studentName("郑十一（有不及格）")
                .partyMember(false)
                .academicYear(2023)
                .grade("硕士")
                .moralScore(new BigDecimal("9.0"))
                .intellectualScore(new BigDecimal("55.0"))
                .sportsScore(new BigDecimal("2.5"))
                .laborScore(new BigDecimal("1.5"))
                .failedCourseCount(1)                 // 有不及格课程
                .hasMajorViolation(false)
                .volunteerHours(0)
                .build());

        // 体育美育不满足（低于2分）
        students.add(StudentEvaluationSummary.builder()
                .studentId("2023010")
                .studentName("王十二（体育美育不足）")
                .partyMember(false)
                .academicYear(2023)
                .grade("硕士")
                .moralScore(new BigDecimal("8.5"))
                .intellectualScore(new BigDecimal("78.0"))
                .sportsScore(new BigDecimal("1.5"))   // 体育美育需2分，未满足
                .laborScore(new BigDecimal("1.5"))
                .failedCourseCount(0)
                .hasMajorViolation(false)
                .volunteerHours(0)
                .build());

        return students;
    }

    public static void printExpectedResults() {
        System.out.println("============================================");
        System.out.println("资格评估 Mock 数据预期结果");
        System.out.println("============================================");
        System.out.println();
        System.out.println("【劳育资格规则】");
        System.out.println("  - 党员学生：劳育分数 >= 1.5 且 志愿服务 >= 8小时");
        System.out.println("  - 非党员学生：劳育分数 >= 1.0");
        System.out.println();
        System.out.println("【德育资格规则】");
        System.out.println("  - 德育分数 >= 8");
        System.out.println("  - 无重大违纪记录");
        System.out.println();
        System.out.println("【智育资格规则】");
        System.out.println("  - 智育分数 >= 60");
        System.out.println("  - 无不及格课程");
        System.out.println();
        System.out.println("【体育美育资格规则】");
        System.out.println("  - 体育美育分数 >= 2");
        System.out.println();
        System.out.println("============================================");
        System.out.println("学生预期结果：");
        System.out.println("============================================");
        
        List<StudentEvaluationSummary> students = getMockStudents();
        for (StudentEvaluationSummary student : students) {
            boolean laborEligible = student.isPartyMember() 
                    ? student.getLaborScore().compareTo(new BigDecimal("1.5")) >= 0 
                        && student.getVolunteerHours() >= 8
                    : student.getLaborScore().compareTo(new BigDecimal("1.0")) >= 0;
            
            boolean moralEligible = student.getMoralScore().compareTo(new BigDecimal("8")) >= 0 
                    && !student.isHasMajorViolation();
            
            boolean intellectualEligible = student.getIntellectualScore().compareTo(new BigDecimal("60")) >= 0 
                    && student.getFailedCourseCount() == 0;
            
            boolean sportsEligible = student.getSportsScore().compareTo(new BigDecimal("2")) >= 0;
            
            System.out.printf("%s\n", student.getStudentName());
            System.out.printf("  学号: %s\n", student.getStudentId());
            System.out.printf("  党员: %s | 劳育分: %s | 志愿: %d小时\n", 
                    student.isPartyMember() ? "是" : "否", 
                    student.getLaborScore(), 
                    student.getVolunteerHours());
            System.out.printf("  预期结果:\n");
            System.out.printf("    劳育: %s\n", laborEligible ? "合格" : "不合格");
            System.out.printf("    德育: %s\n", moralEligible ? "合格" : "不合格");
            System.out.printf("    智育: %s\n", intellectualEligible ? "合格" : "不合格");
            System.out.printf("    体育美育: %s\n", sportsEligible ? "合格" : "不合格");
            System.out.println();
        }
    }

    public static void main(String[] args) {
        printExpectedResults();
    }
}