package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.domain.iam.model.IamScopeRuleDetail;
import edu.whut.eval.domain.iam.repository.ScopeRuleAdminRepository;
import edu.whut.eval.infra.persistence.entity.IamScopeRuleDO;
import edu.whut.eval.infra.persistence.mapper.IamScopeRuleMapper;
import edu.whut.eval.infra.persistence.repository.row.IamScopeRuleAdminRow;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Repository
public class MybatisPlusScopeRuleAdminRepository implements ScopeRuleAdminRepository {

    private final IamScopeRuleMapper iamScopeRuleMapper;
    private final ObjectMapper objectMapper;

    public MybatisPlusScopeRuleAdminRepository(IamScopeRuleMapper iamScopeRuleMapper) {
        this(iamScopeRuleMapper, new ObjectMapper());
    }

    public MybatisPlusScopeRuleAdminRepository(IamScopeRuleMapper iamScopeRuleMapper, ObjectMapper objectMapper) {
        this.iamScopeRuleMapper = iamScopeRuleMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<IamScopeRuleDetail> findByAssignmentId(Long assignmentId) {
        return iamScopeRuleMapper.selectAdminRowsByAssignmentId(assignmentId).stream()
                .map(this::toDetail)
                .toList();
    }

    @Override
    public boolean existsSemanticDuplicate(Long assignmentId,
                                           String permissionCode,
                                           String scopeType,
                                           Long orgUnitId,
                                           String categoryCode,
                                           String itemCode,
                                           Map<String, Object> expressionJson) {
        LambdaQueryWrapper<IamScopeRuleDO> wrapper = new LambdaQueryWrapper<IamScopeRuleDO>()
                .eq(IamScopeRuleDO::getAssignmentId, assignmentId)
                .eq(IamScopeRuleDO::getPermissionCode, permissionCode)
                .eq(IamScopeRuleDO::getScopeType, scopeType)
                .eq(IamScopeRuleDO::getStatus, "ACTIVE");
        appendNullableEquals(wrapper, IamScopeRuleDO::getOrgUnitId, orgUnitId);
        appendNullableEquals(wrapper, IamScopeRuleDO::getCategoryCode, categoryCode);
        appendNullableEquals(wrapper, IamScopeRuleDO::getItemCode, itemCode);
        appendNullableEquals(wrapper, IamScopeRuleDO::getExpressionJson, serialize(expressionJson));
        return iamScopeRuleMapper.selectCount(wrapper) > 0;
    }

    @Override
    public IamScopeRuleDetail create(Long assignmentId,
                                     String permissionCode,
                                     String scopeType,
                                     Long orgUnitId,
                                     String orgUnitName,
                                     String categoryCode,
                                     String itemCode,
                                     Map<String, Object> expressionJson,
                                     Integer priority,
                                     String status) {
        IamScopeRuleDO ruleDO = new IamScopeRuleDO();
        ruleDO.setAssignmentId(assignmentId);
        ruleDO.setPermissionCode(permissionCode);
        ruleDO.setScopeType(scopeType);
        ruleDO.setOrgUnitId(orgUnitId);
        ruleDO.setCategoryCode(categoryCode);
        ruleDO.setItemCode(itemCode);
        ruleDO.setExpressionJson(serialize(expressionJson));
        ruleDO.setPriority(priority);
        ruleDO.setStatus(status);
        ruleDO.setCreatedAt(LocalDateTime.now());
        iamScopeRuleMapper.insert(ruleDO);
        return new IamScopeRuleDetail(
                ruleDO.getId(),
                assignmentId,
                permissionCode,
                scopeType,
                orgUnitId,
                orgUnitName,
                categoryCode,
                itemCode,
                normalize(expressionJson),
                priority,
                status,
                ruleDO.getCreatedAt().toString()
        );
    }

    private IamScopeRuleDetail toDetail(IamScopeRuleAdminRow row) {
        return new IamScopeRuleDetail(
                row.getScopeRuleId(),
                row.getAssignmentId(),
                row.getPermissionCode(),
                row.getScopeType(),
                row.getOrgUnitId(),
                row.getOrgUnitName(),
                row.getCategoryCode(),
                row.getItemCode(),
                deserialize(row.getExpressionJson()),
                row.getPriority(),
                row.getStatus(),
                row.getCreatedAt() == null ? null : row.getCreatedAt().toString()
        );
    }

    private Map<String, Object> deserialize(String expressionJson) {
        if (expressionJson == null || expressionJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(expressionJson, Map.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse iam_scope_rule.expression_json", ex);
        }
    }

    private String serialize(Map<String, Object> expressionJson) {
        if (expressionJson == null || expressionJson.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(expressionJson);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize iam_scope_rule.expression_json", ex);
        }
    }

    private Map<String, Object> normalize(Map<String, Object> expressionJson) {
        return expressionJson == null || expressionJson.isEmpty() ? null : Collections.unmodifiableMap(expressionJson);
    }

    private <T> void appendNullableEquals(LambdaQueryWrapper<IamScopeRuleDO> wrapper,
                                          com.baomidou.mybatisplus.core.toolkit.support.SFunction<IamScopeRuleDO, T> column,
                                          T value) {
        if (value == null) {
            wrapper.isNull(column);
        } else {
            wrapper.eq(column, value);
        }
    }
}
