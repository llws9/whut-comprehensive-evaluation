package edu.whut.eval.app.platform;

import edu.whut.eval.application.platform.query.EvaluationItemResponse;
import edu.whut.eval.application.platform.service.PlatformReadApplicationService;
import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.domain.config.model.EvaluationItemsConfig;
import edu.whut.eval.domain.config.model.PlatformRuleConfig;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import edu.whut.eval.infra.nacos.config.EvaluationItemsConfigProvider;
import edu.whut.eval.infra.nacos.config.NacosTypedConfigConfiguration;
import edu.whut.eval.infra.nacos.config.PlatformRuleConfigProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationItemQueryApplicationServiceTest {

    private final TypedConfigRepository typedConfigRepository = new NacosTypedConfigConfiguration().typedConfigRepository();
    private final PlatformReadApplicationService service = new PlatformReadApplicationService(typedConfigRepository);

    @Test
    void shouldReturnPlatformStatusAndDeadlineFromTypedConfig() {
        PlatformRuleConfig config = new PlatformRuleConfig();
        config.setStudentApplyEnabled(true);
        config.setFinalSubmitEnabled(false);
        config.setStudentApplyDeadline("2026-09-30T23:59:59+08:00");
        config.setFinalSubmitDeadline("2026-10-15T23:59:59+08:00");
        typedConfigRepository.save(PlatformRuleConfigProvider.DEFINITION_NAME, config);

        assertThat(service.getMenuStatus().isStudentApplyEnabled()).isTrue();
        assertThat(service.getMenuStatus().isFinalSubmitEnabled()).isFalse();
        assertThat(service.getMenuStatus().getSource()).isEqualTo("NACOS");
        assertThat(service.getMenuDeadline().getStudentApplyDeadline()).isEqualTo("2026-09-30T23:59:59+08:00");
        assertThat(service.getMenuDeadline().getFinalSubmitDeadline()).isEqualTo("2026-10-15T23:59:59+08:00");
        assertThat(service.getMenuDeadline().getSource()).isEqualTo("NACOS");
    }

    @Test
    void shouldReturnNullDeadlinesWhenAbsent() {
        PlatformRuleConfig config = new PlatformRuleConfig();
        typedConfigRepository.save(PlatformRuleConfigProvider.DEFINITION_NAME, config);

        assertThat(service.getMenuDeadline().getStudentApplyDeadline()).isNull();
        assertThat(service.getMenuDeadline().getFinalSubmitDeadline()).isNull();
    }

    @Test
    void shouldListEnabledItemsWithStableSortAndCategoryFilter() {
        EvaluationItemsConfig config = new EvaluationItemsConfig();
        config.setEvaluationItems(Map.of(
                "INTELLECTUAL", List.of(
                        item("INTELLECTUAL", "智育", "INTELLECTUAL_PAPER", "论文发表", 20, true, "paper"),
                        item("INTELLECTUAL", "智育", "INTELLECTUAL_COMPETITION", "学科竞赛", 10, true, "competition"),
                        item("INTELLECTUAL", "智育", "INTELLECTUAL_DISABLED", "禁用项目", 1, false, "disabled")
                ),
                "MORAL", List.of(
                        item("MORAL", "德育", "MORAL_HONOR", "荣誉表彰", 10, true, "honor")
                )
        ));
        typedConfigRepository.save(EvaluationItemsConfigProvider.DEFINITION_NAME, config);

        List<EvaluationItemResponse> allItems = service.listEvaluationItems(null);
        assertThat(allItems).extracting(EvaluationItemResponse::getItemCode)
                .containsExactly("INTELLECTUAL_COMPETITION", "INTELLECTUAL_PAPER", "MORAL_HONOR");

        List<EvaluationItemResponse> intellectualItems = service.listEvaluationItems("INTELLECTUAL");
        assertThat(intellectualItems).extracting(EvaluationItemResponse::getItemCode)
                .containsExactly("INTELLECTUAL_COMPETITION", "INTELLECTUAL_PAPER");
        assertThat(intellectualItems.getFirst().getMaxPoints()).isEqualByComparingTo("8.00");
        assertThat(intellectualItems.getFirst().getMaxPointsExpression()).isEqualTo("min(raw, 8)");
        assertThat(intellectualItems.getFirst().getOptionsKey()).isEqualTo("competition");
    }

    @Test
    void shouldReturnEmptyListForUnknownCategory() {
        EvaluationItemsConfig config = new EvaluationItemsConfig();
        config.setEvaluationItems(Map.of("MORAL", List.of(item("MORAL", "德育", "MORAL_HONOR", "荣誉表彰", 10, true, "honor"))));
        typedConfigRepository.save(EvaluationItemsConfigProvider.DEFINITION_NAME, config);

        assertThat(service.listEvaluationItems("UNKNOWN")).isEmpty();
    }

    @Test
    void shouldFailWhenRequiredTypedConfigIsMissing() {
        assertThatThrownBy(service::getMenuStatus).isInstanceOf(ConfigLoadException.class);
        assertThatThrownBy(() -> service.listEvaluationItems(null)).isInstanceOf(ConfigLoadException.class);
    }

    private static EvaluationItemsConfig.EvaluationItem item(String categoryCode, String categoryName, String itemCode,
                                                            String itemName, int sortOrder, boolean enabled,
                                                            String optionsKey) {
        EvaluationItemsConfig.EvaluationItem item = new EvaluationItemsConfig.EvaluationItem();
        item.setCategoryCode(categoryCode);
        item.setCategoryName(categoryName);
        item.setItemCode(itemCode);
        item.setItemName(itemName);
        item.setDescription(itemName + "描述");
        item.setMaxPoints(new BigDecimal("8.00"));
        item.setMaxPointsExpression("min(raw, 8)");
        item.setApplyMode("STUDENT_APPLY");
        item.setEnabled(enabled);
        item.setSortOrder(sortOrder);
        item.setOptionsKey(optionsKey);
        return item;
    }
}
