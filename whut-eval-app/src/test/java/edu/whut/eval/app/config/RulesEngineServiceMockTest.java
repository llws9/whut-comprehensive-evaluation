package edu.whut.eval.app.config;

import edu.whut.eval.domain.config.StudentContext;
import edu.whut.eval.domain.config.StudentEvaluationSummary;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import edu.whut.eval.domain.config.model.EligibilityRulesConfig;
import edu.whut.eval.domain.config.model.EvaluationItemsConfig;
import edu.whut.eval.domain.config.model.IndexOptionsConfig;
import edu.whut.eval.infra.nacos.rule.RulesEngineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RulesEngineServiceMockTest {

    private RulesEngineServiceImpl rulesEngineService;
    private TypedConfigRepository configRepository;

    @BeforeEach
    void setUp() {
        configRepository = mock(TypedConfigRepository.class);
        setupMockConfigs();
        rulesEngineService = new RulesEngineServiceImpl(configRepository);
    }

    private void setupMockConfigs() {
        // 设置测评指标配置
        EvaluationItemsConfig evaluationItemsConfig = new EvaluationItemsConfig();
        Map<String, List<EvaluationItemsConfig.EvaluationItem>> items = new HashMap<>();
        
        EvaluationItemsConfig.EvaluationItem moralItem = new EvaluationItemsConfig.EvaluationItem();
        moralItem.setItemCode("MORAL_REWARD_PUNISHMENT");
        moralItem.setMaxPoints(new BigDecimal("6"));
        moralItem.setMaxPointsExpression("isPartyMember ? 8 : 6");
        moralItem.setOptionsKey("moral-reward-punishment");
        
        List<EvaluationItemsConfig.EvaluationItem> moralItems = new ArrayList<>();
        moralItems.add(moralItem);
        items.put("MORAL", moralItems);

        EvaluationItemsConfig.EvaluationItem sportsOtherItem = new EvaluationItemsConfig.EvaluationItem();
        sportsOtherItem.setItemCode("SPORTS_OTHER");
        sportsOtherItem.setMaxPoints(new BigDecimal("3"));
        sportsOtherItem.setOptionsKey("sports-other");

        List<EvaluationItemsConfig.EvaluationItem> sportsItems = new ArrayList<>();
        sportsItems.add(sportsOtherItem);
        items.put("SPORTS", sportsItems);
        evaluationItemsConfig.setEvaluationItems(items);

        // 设置指标选项配置
        IndexOptionsConfig indexOptionsConfig = new IndexOptionsConfig();
        Map<String, List<IndexOptionsConfig.OptionItem>> options = new HashMap<>();
        
        IndexOptionsConfig.OptionItem rewardOption = new IndexOptionsConfig.OptionItem();
        rewardOption.setOptionCode("REWARD_PROVINCIAL");
        rewardOption.setPoints(new BigDecimal("3"));
        rewardOption.setAllowCustomPoints(false);
        
        List<IndexOptionsConfig.OptionItem> moralOptions = new ArrayList<>();
        moralOptions.add(rewardOption);
        options.put("moral-reward-punishment", moralOptions);
        
        // 其他类别配置（允许自定义分值）
        IndexOptionsConfig.OptionItem otherOption = new IndexOptionsConfig.OptionItem();
        otherOption.setOptionCode("OTHER_CUSTOM");
        otherOption.setPoints(null);
        otherOption.setAllowCustomPoints(true);
        
        List<IndexOptionsConfig.OptionItem> otherOptions = new ArrayList<>();
        otherOptions.add(otherOption);
        options.put("sports-other", otherOptions);
        
        indexOptionsConfig.setIndexOptions(options);

        // 设置资格规则配置
        EligibilityRulesConfig eligibilityRulesConfig = new EligibilityRulesConfig();
        Map<String, List<EligibilityRulesConfig.EligibilityRuleItem>> rules = new HashMap<>();
        
        // 劳育规则 - 党员差异化
        List<EligibilityRulesConfig.EligibilityRuleItem> laborRules = new ArrayList<>();
        
        EligibilityRulesConfig.EligibilityRuleItem laborRule1 = new EligibilityRulesConfig.EligibilityRuleItem();
        laborRule1.setRuleId("LABOR_RULE_1");
        laborRule1.setExpression("isPartyMember ? (laborScore >= 1.5) : (laborScore >= 1.0)");
        laborRule1.setEnabled(true);
        
        EligibilityRulesConfig.EligibilityRuleItem laborRule2 = new EligibilityRulesConfig.EligibilityRuleItem();
        laborRule2.setRuleId("LABOR_RULE_2");
        laborRule2.setExpression("isPartyMember ? (volunteerHours >= 8) : true");
        laborRule2.setEnabled(true);
        
        laborRules.add(laborRule1);
        laborRules.add(laborRule2);
        rules.put("LABOR", laborRules);

        // 德育规则
        List<EligibilityRulesConfig.EligibilityRuleItem> moralRules = new ArrayList<>();
        
        EligibilityRulesConfig.EligibilityRuleItem moralRule1 = new EligibilityRulesConfig.EligibilityRuleItem();
        moralRule1.setRuleId("MORAL_RULE_1");
        moralRule1.setExpression("moralScore >= 8");
        moralRule1.setEnabled(true);
        
        EligibilityRulesConfig.EligibilityRuleItem moralRule2 = new EligibilityRulesConfig.EligibilityRuleItem();
        moralRule2.setRuleId("MORAL_RULE_2");
        moralRule2.setExpression("hasMajorViolation == false");
        moralRule2.setEnabled(true);
        
        moralRules.add(moralRule1);
        moralRules.add(moralRule2);
        rules.put("MORAL", moralRules);

        // 智育规则
        List<EligibilityRulesConfig.EligibilityRuleItem> intellectualRules = new ArrayList<>();
        
        EligibilityRulesConfig.EligibilityRuleItem intellectualRule1 = new EligibilityRulesConfig.EligibilityRuleItem();
        intellectualRule1.setRuleId("INTELLECTUAL_RULE_1");
        intellectualRule1.setExpression("intellectualScore >= 60");
        intellectualRule1.setEnabled(true);
        
        EligibilityRulesConfig.EligibilityRuleItem intellectualRule2 = new EligibilityRulesConfig.EligibilityRuleItem();
        intellectualRule2.setRuleId("INTELLECTUAL_RULE_2");
        intellectualRule2.setExpression("failedCourseCount == 0");
        intellectualRule2.setEnabled(true);
        
        intellectualRules.add(intellectualRule1);
        intellectualRules.add(intellectualRule2);
        rules.put("INTELLECTUAL", intellectualRules);

        // 体育美育规则
        List<EligibilityRulesConfig.EligibilityRuleItem> sportsRules = new ArrayList<>();
        
        EligibilityRulesConfig.EligibilityRuleItem sportsRule1 = new EligibilityRulesConfig.EligibilityRuleItem();
        sportsRule1.setRuleId("SPORTS_RULE_1");
        sportsRule1.setExpression("sportsCompetitionScore + sportsArtContributionScore >= 0.2");
        sportsRule1.setEnabled(true);
        
        sportsRules.add(sportsRule1);
        rules.put("SPORTS", sportsRules);
        
        eligibilityRulesConfig.setEligibilityRules(rules);

        when(configRepository.find("evaluation-items-config", EvaluationItemsConfig.class))
                .thenReturn(java.util.Optional.of(evaluationItemsConfig));
        when(configRepository.find("index-options-config", IndexOptionsConfig.class))
                .thenReturn(java.util.Optional.of(indexOptionsConfig));
        when(configRepository.find("eligibility-rules-config", EligibilityRulesConfig.class))
                .thenReturn(java.util.Optional.of(eligibilityRulesConfig));
    }

    @Test
    @DisplayName("测试最高分值SpEL表达式 - 党员学生")
    void testMaxPointsExpression_PartyMember() {
        StudentContext context = StudentContext.builder()
                .studentId("2023001")
                .partyMember(true)
                .build();

        BigDecimal maxPoints = rulesEngineService.calculateMaxPoints("MORAL_REWARD_PUNISHMENT", context);

        assertEquals(0, new BigDecimal("8").compareTo(maxPoints));
    }

    @Test
    @DisplayName("测试最高分值SpEL表达式 - 非党员学生")
    void testMaxPointsExpression_NonPartyMember() {
        StudentContext context = StudentContext.builder()
                .studentId("2023004")
                .partyMember(false)
                .build();

        BigDecimal maxPoints = rulesEngineService.calculateMaxPoints("MORAL_REWARD_PUNISHMENT", context);

        assertEquals(0, new BigDecimal("6").compareTo(maxPoints));
    }

    @Test
    @DisplayName("测试劳育资格评估 - 党员满足条件")
    void testLaborEligibility_PartyMember_Satisfied() {
        StudentEvaluationSummary summary = StudentEvaluationSummary.builder()
                .studentId("2023001")
                .partyMember(true)
                .laborScore(new BigDecimal("2.0"))
                .volunteerHours(10)
                .build();

        boolean eligible = rulesEngineService.evaluateEligibility("LABOR", summary);

        assertTrue(eligible);
    }

    @Test
    @DisplayName("测试劳育资格评估 - 党员劳育分不足")
    void testLaborEligibility_PartyMember_LaborScoreInsufficient() {
        StudentEvaluationSummary summary = StudentEvaluationSummary.builder()
                .studentId("2023002")
                .partyMember(true)
                .laborScore(new BigDecimal("1.0"))  // 党员需1.5分
                .volunteerHours(5)
                .build();

        boolean eligible = rulesEngineService.evaluateEligibility("LABOR", summary);

        assertFalse(eligible);
    }

    @Test
    @DisplayName("测试劳育资格评估 - 党员志愿时长不足")
    void testLaborEligibility_PartyMember_VolunteerHoursInsufficient() {
        StudentEvaluationSummary summary = StudentEvaluationSummary.builder()
                .studentId("2023003")
                .partyMember(true)
                .laborScore(new BigDecimal("3.0"))
                .volunteerHours(5)  // 党员需8小时
                .build();

        boolean eligible = rulesEngineService.evaluateEligibility("LABOR", summary);

        assertFalse(eligible);
    }

    @Test
    @DisplayName("测试劳育资格评估 - 非党员满足条件")
    void testLaborEligibility_NonPartyMember_Satisfied() {
        StudentEvaluationSummary summary = StudentEvaluationSummary.builder()
                .studentId("2023004")
                .partyMember(false)
                .laborScore(new BigDecimal("1.5"))  // 非党员需1分
                .volunteerHours(0)
                .build();

        boolean eligible = rulesEngineService.evaluateEligibility("LABOR", summary);

        assertTrue(eligible);
    }

    @Test
    @DisplayName("测试劳育资格评估 - 非党员不满足条件")
    void testLaborEligibility_NonPartyMember_Insufficient() {
        StudentEvaluationSummary summary = StudentEvaluationSummary.builder()
                .studentId("2023005")
                .partyMember(false)
                .laborScore(new BigDecimal("0.8"))  // 非党员需1分
                .volunteerHours(0)
                .build();

        boolean eligible = rulesEngineService.evaluateEligibility("LABOR", summary);

        assertFalse(eligible);
    }

    @Test
    @DisplayName("测试劳育资格评估 - 边界条件：党员刚好1.5分")
    void testLaborEligibility_PartyMember_Boundary1_5() {
        StudentEvaluationSummary summary = StudentEvaluationSummary.builder()
                .studentId("2023006")
                .partyMember(true)
                .laborScore(new BigDecimal("1.5"))
                .volunteerHours(8)
                .build();

        boolean eligible = rulesEngineService.evaluateEligibility("LABOR", summary);

        assertTrue(eligible);
    }

    @Test
    @DisplayName("测试劳育资格评估 - 边界条件：非党员刚好1.0分")
    void testLaborEligibility_NonPartyMember_Boundary1_0() {
        StudentEvaluationSummary summary = StudentEvaluationSummary.builder()
                .studentId("2023007")
                .partyMember(false)
                .laborScore(new BigDecimal("1.0"))
                .volunteerHours(0)
                .build();

        boolean eligible = rulesEngineService.evaluateEligibility("LABOR", summary);

        assertTrue(eligible);
    }

    @Test
    @DisplayName("测试其他类别允许自定义分值")
    void testAllowsCustomPoints_OtherCategory() {
        boolean allowsCustom = rulesEngineService.allowsCustomPoints("SPORTS_OTHER", "OTHER_CUSTOM");

        assertTrue(allowsCustom);
    }

    @Test
    @DisplayName("测试普通评分档位从配置自动解析分值")
    void testCalculatePoints_NormalOption() {
        BigDecimal points = rulesEngineService.calculatePoints(
                "MORAL_REWARD_PUNISHMENT",
                "REWARD_PROVINCIAL",
                StudentContext.builder().build()
        );

        assertEquals(new BigDecimal("3"), points);
    }

    @Test
    @DisplayName("测试其他类别返回null表示需要自定义分值")
    void testCalculatePoints_OtherCustomOption() {
        BigDecimal points = rulesEngineService.calculatePoints(
                "SPORTS_OTHER",
                "OTHER_CUSTOM",
                StudentContext.builder().build()
        );

        assertNull(points);
    }

    @Test
    @DisplayName("测试普通选项不允许自定义分值")
    void testAllowsCustomPoints_NormalOption() {
        boolean allowsCustom = rulesEngineService.allowsCustomPoints("MORAL_REWARD_PUNISHMENT", "REWARD_PROVINCIAL");

        assertFalse(allowsCustom);
    }

    @Test
    @DisplayName("测试德育资格评估 - 满足条件")
    void testMoralEligibility_Satisfied() {
        StudentEvaluationSummary summary = StudentEvaluationSummary.builder()
                .studentId("2023001")
                .moralScore(new BigDecimal("9.0"))
                .hasMajorViolation(false)
                .build();

        boolean eligible = rulesEngineService.evaluateEligibility("MORAL", summary);

        assertTrue(eligible);
    }

    @Test
    @DisplayName("测试德育资格评估 - 分数不足")
    void testMoralEligibility_InsufficientScore() {
        StudentEvaluationSummary summary = StudentEvaluationSummary.builder()
                .studentId("2023008")
                .moralScore(new BigDecimal("7.0"))  // 需8分
                .hasMajorViolation(false)
                .build();

        boolean eligible = rulesEngineService.evaluateEligibility("MORAL", summary);

        assertFalse(eligible);
    }

    @Test
    @DisplayName("测试智育资格评估 - 满足条件")
    void testIntellectualEligibility_Satisfied() {
        StudentEvaluationSummary summary = StudentEvaluationSummary.builder()
                .studentId("2023001")
                .intellectualScore(new BigDecimal("75.0"))
                .failedCourseCount(0)
                .build();

        boolean eligible = rulesEngineService.evaluateEligibility("INTELLECTUAL", summary);

        assertTrue(eligible);
    }

    @Test
    @DisplayName("测试智育资格评估 - 有不及格课程")
    void testIntellectualEligibility_FailedCourse() {
        StudentEvaluationSummary summary = StudentEvaluationSummary.builder()
                .studentId("2023009")
                .intellectualScore(new BigDecimal("55.0"))
                .failedCourseCount(1)
                .build();

        boolean eligible = rulesEngineService.evaluateEligibility("INTELLECTUAL", summary);

        assertFalse(eligible);
    }

    @Test
    @DisplayName("测试体育美育资格评估 - 满足条件")
    void testSportsEligibility_Satisfied() {
        StudentEvaluationSummary summary = StudentEvaluationSummary.builder()
                .studentId("2023001")
                .sportsCompetitionScore(new BigDecimal("0.2"))
                .sportsArtContributionScore(BigDecimal.ZERO)
                .build();

        boolean eligible = rulesEngineService.evaluateEligibility("SPORTS", summary);

        assertTrue(eligible);
    }

    @Test
    @DisplayName("测试体育美育资格评估 - 分数不足")
    void testSportsEligibility_InsufficientScore() {
        StudentEvaluationSummary summary = StudentEvaluationSummary.builder()
                .studentId("2023010")
                .sportsCompetitionScore(new BigDecimal("0.1"))
                .sportsArtContributionScore(BigDecimal.ZERO)
                .build();

        boolean eligible = rulesEngineService.evaluateEligibility("SPORTS", summary);

        assertFalse(eligible);
    }

    @Test
    @DisplayName("测试体育美育资格评估 - 文体竞赛与文艺征稿合计满足条件")
    void testSportsEligibility_SumSatisfied() {
        StudentEvaluationSummary summary = StudentEvaluationSummary.builder()
                .studentId("2023011")
                .sportsCompetitionScore(new BigDecimal("0.1"))
                .sportsArtContributionScore(new BigDecimal("0.1"))
                .build();

        boolean eligible = rulesEngineService.evaluateEligibility("SPORTS", summary);

        assertTrue(eligible);
    }
}
