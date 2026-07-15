package edu.whut.eval.app.platform;

import edu.whut.eval.application.platform.command.CreateEvaluationItemCommand;
import edu.whut.eval.application.platform.command.PatchEvaluationItemCommand;
import edu.whut.eval.application.platform.query.EvaluationItemResponse;
import edu.whut.eval.application.platform.service.EvaluationItemCommandApplicationService;
import edu.whut.eval.application.platform.service.EvaluationItemsConfigPublisher;
import edu.whut.eval.application.platform.service.ConfigPublishException;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.config.model.EvaluationItemsConfig;
import edu.whut.eval.infra.nacos.InMemoryTypedConfigRepository;
import edu.whut.eval.infra.nacos.config.EvaluationItemsConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationItemCommandApplicationServiceTest {

    private final InMemoryTypedConfigRepository typedConfigRepository = new InMemoryTypedConfigRepository();
    private final StubEvaluationItemsConfigPublisher publisher = new StubEvaluationItemsConfigPublisher();
    private final EvaluationItemCommandApplicationService service =
            new EvaluationItemCommandApplicationService(typedConfigRepository, publisher);

    @BeforeEach
    void setUpConfig() {
        EvaluationItemsConfig config = new EvaluationItemsConfig();
        config.setEvaluationItems(Map.of(
                "INTELLECTUAL", List.of(item("INTELLECTUAL", "智育", "INTELLECTUAL_PAPER", "论文发表", 20, true)),
                "MORAL", List.of(item("MORAL", "德育", "MORAL_HONOR", "荣誉表彰", 10, true))
        ));
        typedConfigRepository.save(EvaluationItemsConfigProvider.DEFINITION_NAME, config);
        publisher.publishResult = true;
        publisher.published = null;
        publisher.reason = null;
    }

    @Test
    void shouldCreateEvaluationItemUnderExistingCategoryAndPublishSnapshot() {
        EvaluationItemResponse result = service.create(new CreateEvaluationItemCommand(
                "INTELLECTUAL",
                "INTELLECTUAL_PATENT",
                "专利授权",
                "发明专利加分",
                new BigDecimal("8.00"),
                "min(raw, 8)",
                "STUDENT_APPLY",
                null,
                "ACTIVE",
                30,
                "intellectual-patent"
        ));

        assertThat(result.getCategoryCode()).isEqualTo("INTELLECTUAL");
        assertThat(result.getCategoryName()).isEqualTo("智育");
        assertThat(result.getItemCode()).isEqualTo("INTELLECTUAL_PATENT");
        assertThat(result.getItemName()).isEqualTo("专利授权");
        assertThat(result.getDescription()).isEqualTo("发明专利加分");
        assertThat(result.getMaxPoints()).isEqualByComparingTo("8.00");
        assertThat(result.getMaxPointsExpression()).isEqualTo("min(raw, 8)");
        assertThat(result.getApplyMode()).isEqualTo("STUDENT_APPLY");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getSortOrder()).isEqualTo(30);
        assertThat(result.getOptionsKey()).isEqualTo("intellectual-patent");
        assertThat(publisher.reason).isEqualTo("create evaluation item INTELLECTUAL_PATENT");
        assertThat(itemCodes(publisher.published, "INTELLECTUAL"))
                .containsExactly("INTELLECTUAL_PAPER", "INTELLECTUAL_PATENT");
        assertThat(itemCodes(currentConfig(), "INTELLECTUAL"))
                .containsExactly("INTELLECTUAL_PAPER", "INTELLECTUAL_PATENT");
    }

    @Test
    void shouldRejectCreateWhenItemCodeAlreadyExistsInAnyCategory() {
        assertThatThrownBy(() -> service.create(new CreateEvaluationItemCommand(
                "MORAL",
                "INTELLECTUAL_PAPER",
                "重复论文",
                null,
                new BigDecimal("6.00"),
                null,
                "STUDENT_APPLY",
                true,
                null,
                11,
                null
        )))
                .isInstanceOf(ConflictException.class)
                .hasMessage("itemCode 已存在: INTELLECTUAL_PAPER");
    }

    @Test
    void shouldRejectCreateWhenCategoryDoesNotExist() {
        assertThatThrownBy(() -> service.create(new CreateEvaluationItemCommand(
                "UNKNOWN",
                "UNKNOWN_ITEM",
                "未知项目",
                null,
                new BigDecimal("1.00"),
                null,
                "STUDENT_APPLY",
                true,
                null,
                1,
                null
        )))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("categoryCode 无效: UNKNOWN");
    }

    @Test
    void shouldPatchEvaluationItemAndPreserveUnspecifiedFields() {
        EvaluationItemResponse result = service.patch(new PatchEvaluationItemCommand(
                "INTELLECTUAL_PAPER",
                "高水平论文",
                null,
                new BigDecimal("10.00"),
                null,
                null,
                false,
                "INACTIVE",
                25,
                null
        ));

        assertThat(result.getItemCode()).isEqualTo("INTELLECTUAL_PAPER");
        assertThat(result.getItemName()).isEqualTo("高水平论文");
        assertThat(result.getDescription()).isEqualTo("论文发表描述");
        assertThat(result.getMaxPoints()).isEqualByComparingTo("10.00");
        assertThat(result.getMaxPointsExpression()).isEqualTo("min(raw, 6)");
        assertThat(result.getApplyMode()).isEqualTo("STUDENT_APPLY");
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getSortOrder()).isEqualTo(25);
        assertThat(result.getOptionsKey()).isEqualTo("INTELLECTUAL_PAPER-options");
        assertThat(publisher.reason).isEqualTo("patch evaluation item INTELLECTUAL_PAPER");
    }

    @Test
    void shouldRejectPatchWithoutAnyMutableField() {
        assertThatThrownBy(() -> service.patch(new PatchEvaluationItemCommand(
                "INTELLECTUAL_PAPER",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(ValidationException.class)
                .hasMessage("至少提供一个项目定义字段");
    }

    @Test
    void shouldFailWhenEvaluationItemsPublishFails() {
        publisher.publishResult = false;

        assertThatThrownBy(() -> service.patch(new PatchEvaluationItemCommand(
                "INTELLECTUAL_PAPER",
                "高水平论文",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(ConfigPublishException.class)
                .hasMessage("Failed to publish evaluation-items-config");
    }

    private EvaluationItemsConfig currentConfig() {
        return typedConfigRepository.find(EvaluationItemsConfigProvider.DEFINITION_NAME, EvaluationItemsConfig.class)
                .orElseThrow();
    }

    private List<String> itemCodes(EvaluationItemsConfig config, String categoryCode) {
        return config.getEvaluationItems().get(categoryCode).stream()
                .map(EvaluationItemsConfig.EvaluationItem::getItemCode)
                .toList();
    }

    private static EvaluationItemsConfig.EvaluationItem item(String categoryCode,
                                                            String categoryName,
                                                            String itemCode,
                                                            String itemName,
                                                            int sortOrder,
                                                            boolean enabled) {
        EvaluationItemsConfig.EvaluationItem item = new EvaluationItemsConfig.EvaluationItem();
        item.setCategoryCode(categoryCode);
        item.setCategoryName(categoryName);
        item.setItemCode(itemCode);
        item.setItemName(itemName);
        item.setDescription(itemName + "描述");
        item.setMaxPoints(new BigDecimal("6.00"));
        item.setMaxPointsExpression("min(raw, 6)");
        item.setApplyMode("STUDENT_APPLY");
        item.setEnabled(enabled);
        item.setSortOrder(sortOrder);
        item.setOptionsKey(itemCode + "-options");
        return item;
    }

    private static final class StubEvaluationItemsConfigPublisher implements EvaluationItemsConfigPublisher {

        private EvaluationItemsConfig published;
        private String reason;
        private boolean publishResult = true;

        @Override
        public boolean publish(EvaluationItemsConfig config, String reason, OffsetDateTime effectiveAt) {
            this.published = config;
            this.reason = reason;
            return publishResult;
        }
    }
}
