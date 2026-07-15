package edu.whut.eval.application.platform.service;

import edu.whut.eval.application.platform.command.CreateEvaluationItemCommand;
import edu.whut.eval.application.platform.command.PatchEvaluationItemCommand;
import edu.whut.eval.application.platform.query.EvaluationItemCommandResult;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.config.model.EvaluationItemsConfig;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class EvaluationItemCommandApplicationService {

    public static final String EVALUATION_ITEMS_CONFIG = "evaluation-items-config";
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final Set<String> SUPPORTED_APPLY_MODES = Set.of("STUDENT_APPLY", "TEACHER_IMPORT", "MIXED");
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";

    private final TypedConfigRepository typedConfigRepository;
    private final EvaluationItemsConfigPublisher evaluationItemsConfigPublisher;

    public EvaluationItemCommandApplicationService(TypedConfigRepository typedConfigRepository,
                                                   EvaluationItemsConfigPublisher evaluationItemsConfigPublisher) {
        this.typedConfigRepository = typedConfigRepository;
        this.evaluationItemsConfigPublisher = evaluationItemsConfigPublisher;
    }

    public EvaluationItemCommandResult create(CreateEvaluationItemCommand command) {
        validateCreate(command);
        EvaluationItemsConfig next = copyOf(requiredConfig());
        Map<String, List<EvaluationItemsConfig.EvaluationItem>> itemsByCategory = safeMutableItems(next);
        String categoryCode = normalizeCode(command.categoryCode());
        List<EvaluationItemsConfig.EvaluationItem> categoryItems = itemsByCategory.get(categoryCode);
        if (categoryItems == null) {
            throw new ResourceNotFoundException("categoryCode 无效: " + categoryCode);
        }
        String itemCode = normalizeCode(command.itemCode());
        if (findItem(itemsByCategory, itemCode) != null) {
            throw new ConflictException("itemCode 已存在: " + itemCode);
        }

        EvaluationItemsConfig.EvaluationItem template = categoryItems.isEmpty() ? null : categoryItems.getFirst();
        EvaluationItemsConfig.EvaluationItem item = new EvaluationItemsConfig.EvaluationItem();
        item.setCategoryCode(categoryCode);
        item.setCategoryName(template == null ? null : template.getCategoryName());
        item.setItemCode(itemCode);
        item.setItemName(command.itemName().trim());
        item.setDescription(trimToNull(command.description()));
        item.setMaxPoints(command.maxPoints());
        item.setMaxPointsExpression(trimToNull(command.maxPointsExpression()));
        item.setApplyMode(normalizeApplyMode(command.applyMode()));
        item.setEnabled(resolveEnabled(command.enabled(), command.status(), true));
        item.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        item.setOptionsKey(trimToNull(command.optionsKey()));
        categoryItems.add(item);

        publish(next, "create evaluation item " + itemCode);
        return toResult(item);
    }

    public EvaluationItemCommandResult patch(PatchEvaluationItemCommand command) {
        validatePatch(command);
        EvaluationItemsConfig next = copyOf(requiredConfig());
        FoundItem found = findItem(safeMutableItems(next), normalizeCode(command.itemCode()));
        if (found == null) {
            throw new ResourceNotFoundException("itemCode 无效: " + normalizeCode(command.itemCode()));
        }

        EvaluationItemsConfig.EvaluationItem item = found.item();
        if (command.itemName() != null) {
            if (command.itemName().isBlank()) {
                throw new ValidationException("itemName 不能为空");
            }
            item.setItemName(command.itemName().trim());
        }
        if (command.description() != null) {
            item.setDescription(trimToNull(command.description()));
        }
        if (command.maxPoints() != null) {
            validateMaxPoints(command.maxPoints());
            item.setMaxPoints(command.maxPoints());
        }
        if (command.maxPointsExpression() != null) {
            item.setMaxPointsExpression(trimToNull(command.maxPointsExpression()));
        }
        if (command.applyMode() != null) {
            item.setApplyMode(normalizeApplyMode(command.applyMode()));
        }
        if (command.enabled() != null || command.status() != null) {
            item.setEnabled(resolveEnabled(command.enabled(), command.status(), item.isEnabled()));
        }
        if (command.sortOrder() != null) {
            item.setSortOrder(command.sortOrder());
        }
        if (command.optionsKey() != null) {
            item.setOptionsKey(trimToNull(command.optionsKey()));
        }

        publish(next, "patch evaluation item " + item.getItemCode());
        return toResult(item);
    }

    private void validateCreate(CreateEvaluationItemCommand command) {
        if (command == null) {
            throw new ValidationException("请求不能为空");
        }
        requireCode(command.categoryCode(), "categoryCode");
        requireCode(command.itemCode(), "itemCode");
        if (isBlank(command.itemName())) {
            throw new ValidationException("itemName 不能为空");
        }
        normalizeApplyMode(command.applyMode());
        if (command.maxPoints() != null) {
            validateMaxPoints(command.maxPoints());
        }
        resolveEnabled(command.enabled(), command.status(), true);
    }

    private void validatePatch(PatchEvaluationItemCommand command) {
        if (command == null) {
            throw new ValidationException("请求不能为空");
        }
        requireCode(command.itemCode(), "itemCode");
        if (command.itemName() == null
                && command.description() == null
                && command.maxPoints() == null
                && command.maxPointsExpression() == null
                && command.applyMode() == null
                && command.enabled() == null
                && command.status() == null
                && command.sortOrder() == null
                && command.optionsKey() == null) {
            throw new ValidationException("至少提供一个项目定义字段");
        }
    }

    private void publish(EvaluationItemsConfig config, String reason) {
        OffsetDateTime effectiveAt = OffsetDateTime.now(ZONE_ID);
        boolean published = evaluationItemsConfigPublisher.publish(config, reason, effectiveAt);
        if (!published) {
            throw new ConfigPublishException("Failed to publish " + EVALUATION_ITEMS_CONFIG);
        }
        typedConfigRepository.save(EVALUATION_ITEMS_CONFIG, copyOf(config));
    }

    private EvaluationItemsConfig requiredConfig() {
        return typedConfigRepository.find(EVALUATION_ITEMS_CONFIG, EvaluationItemsConfig.class)
                .orElseThrow(() -> new ConfigLoadException("Required typed config not found: " + EVALUATION_ITEMS_CONFIG));
    }

    private Map<String, List<EvaluationItemsConfig.EvaluationItem>> safeMutableItems(EvaluationItemsConfig config) {
        if (config.getEvaluationItems() == null) {
            config.setEvaluationItems(new LinkedHashMap<>());
        }
        return config.getEvaluationItems();
    }

    private EvaluationItemsConfig copyOf(EvaluationItemsConfig source) {
        EvaluationItemsConfig copy = new EvaluationItemsConfig();
        Map<String, List<EvaluationItemsConfig.EvaluationItem>> copiedItems = new LinkedHashMap<>();
        if (source.getEvaluationItems() != null) {
            source.getEvaluationItems().forEach((categoryCode, items) -> {
                List<EvaluationItemsConfig.EvaluationItem> copiedCategoryItems = new ArrayList<>();
                if (items != null) {
                    for (EvaluationItemsConfig.EvaluationItem item : items) {
                        copiedCategoryItems.add(copyItem(item));
                    }
                }
                copiedItems.put(categoryCode, copiedCategoryItems);
            });
        }
        copy.setEvaluationItems(copiedItems);
        return copy;
    }

    private EvaluationItemsConfig.EvaluationItem copyItem(EvaluationItemsConfig.EvaluationItem source) {
        EvaluationItemsConfig.EvaluationItem copy = new EvaluationItemsConfig.EvaluationItem();
        copy.setItemCode(source.getItemCode());
        copy.setItemName(source.getItemName());
        copy.setCategoryCode(source.getCategoryCode());
        copy.setCategoryName(source.getCategoryName());
        copy.setDescription(source.getDescription());
        copy.setMaxPoints(source.getMaxPoints());
        copy.setMaxPointsExpression(source.getMaxPointsExpression());
        copy.setScholarshipRequirement(source.getScholarshipRequirement());
        copy.setApplyMode(source.getApplyMode());
        copy.setEnabled(source.isEnabled());
        copy.setSortOrder(source.getSortOrder());
        copy.setOptionsKey(source.getOptionsKey());
        return copy;
    }

    private FoundItem findItem(Map<String, List<EvaluationItemsConfig.EvaluationItem>> itemsByCategory, String itemCode) {
        for (Map.Entry<String, List<EvaluationItemsConfig.EvaluationItem>> entry : itemsByCategory.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            for (EvaluationItemsConfig.EvaluationItem item : entry.getValue()) {
                if (itemCode.equals(item.getItemCode())) {
                    return new FoundItem(entry.getKey(), item);
                }
            }
        }
        return null;
    }

    private EvaluationItemCommandResult toResult(EvaluationItemsConfig.EvaluationItem item) {
        return new EvaluationItemCommandResult(
                item.getCategoryCode(),
                item.getCategoryName(),
                item.getItemCode(),
                item.getItemName(),
                item.getDescription(),
                item.getMaxPoints(),
                item.getMaxPointsExpression(),
                item.getApplyMode(),
                item.isEnabled(),
                item.isEnabled() ? STATUS_ACTIVE : STATUS_INACTIVE,
                item.getSortOrder(),
                item.getOptionsKey()
        );
    }

    private String normalizeApplyMode(String value) {
        String normalized = normalizeCode(value);
        if (!SUPPORTED_APPLY_MODES.contains(normalized)) {
            throw new ValidationException("applyMode 仅允许 STUDENT_APPLY、TEACHER_IMPORT 或 MIXED");
        }
        return normalized;
    }

    private boolean resolveEnabled(Boolean enabled, String status, boolean defaultValue) {
        if (status == null || status.isBlank()) {
            return enabled == null ? defaultValue : enabled;
        }
        String normalizedStatus = normalizeCode(status);
        boolean statusEnabled;
        if (STATUS_ACTIVE.equals(normalizedStatus)) {
            statusEnabled = true;
        } else if (STATUS_INACTIVE.equals(normalizedStatus)) {
            statusEnabled = false;
        } else {
            throw new ValidationException("status 仅允许 ACTIVE 或 INACTIVE");
        }
        if (enabled != null && enabled != statusEnabled) {
            throw new ValidationException("enabled 与 status 不一致");
        }
        return statusEnabled;
    }

    private void validateMaxPoints(BigDecimal value) {
        if (value.signum() < 0) {
            throw new ValidationException("maxPoints 不能小于 0");
        }
    }

    private void requireCode(String value, String fieldName) {
        if (isBlank(value)) {
            throw new ValidationException(fieldName + " 不能为空");
        }
    }

    private String normalizeCode(String value) {
        if (isBlank(value)) {
            throw new ValidationException("编码不能为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record FoundItem(String categoryCode, EvaluationItemsConfig.EvaluationItem item) {
    }
}
