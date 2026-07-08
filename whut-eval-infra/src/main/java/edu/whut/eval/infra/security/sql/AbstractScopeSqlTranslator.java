package edu.whut.eval.infra.security.sql;

import edu.whut.eval.domain.auth.model.ScopeExpression;
import edu.whut.eval.domain.auth.model.ScopeExpressionCondition;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.ScopeRuleExpressionInterpreter;
import edu.whut.eval.common.exception.BizRuleViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

abstract class AbstractScopeSqlTranslator {

    private final ScopeRuleExpressionInterpreter scopeRuleExpressionInterpreter;

    protected AbstractScopeSqlTranslator(ScopeRuleExpressionInterpreter scopeRuleExpressionInterpreter) {
        this.scopeRuleExpressionInterpreter = scopeRuleExpressionInterpreter;
    }

    protected String addParameter(Map<String, Object> parameters, Object value) {
        String key = "p" + (parameters.size() + 1);
        parameters.put(key, value);
        return "#{parameters." + key + "}";
    }

    protected String translateOrgSubtreePathPredicate(String orgPathColumn,
                                                      Long rootOrgUnitId,
                                                      Map<String, Object> parameters) {
        String rootIdParameter = addParameter(parameters, rootOrgUnitId);
        String rootPath = "(SELECT scope_root.path FROM org_unit scope_root WHERE scope_root.id = " + rootIdParameter + ")";
        return "(" + orgPathColumn + " = " + rootPath
                + " OR " + orgPathColumn + " LIKE CONCAT(" + rootPath + ", '/%'))";
    }

    protected String translateCustomExpression(UserAuthorizationContext authorizationContext,
                                               String expressionJson,
                                               Map<String, String> fieldMapping,
                                               Map<String, Object> parameters) {
        ScopeExpression expression = scopeRuleExpressionInterpreter.parse(expressionJson);
        if (expression.isEmpty()) {
            throw new BizRuleViolationException("CUSTOM_EXPRESSION 不允许为空表达式");
        }
        List<String> parts = new ArrayList<>();
        for (ScopeExpressionCondition condition : expression.getAllOf()) {
            parts.add(translateCondition(authorizationContext, condition, fieldMapping, parameters));
        }
        return "(" + String.join(" AND ", parts) + ")";
    }

    private String translateCondition(UserAuthorizationContext authorizationContext,
                                      ScopeExpressionCondition condition,
                                      Map<String, String> fieldMapping,
                                      Map<String, Object> parameters) {
        String column = fieldMapping.get(condition.getField());
        if (column == null || column.isBlank()) {
            throw new BizRuleViolationException("CUSTOM_EXPRESSION 不支持字段: " + condition.getField());
        }
        String operator = normalize(condition.getOperator());
        if ("EQ".equals(operator)) {
            Object value = resolveValue(authorizationContext, condition.getValue(), condition.getValueFrom());
            return column + " = " + addParameter(parameters, value);
        }
        if ("IN".equals(operator)) {
            List<Object> values = new ArrayList<>();
            if (condition.getValueFrom() != null && !condition.getValueFrom().isBlank()) {
                values.add(resolveValue(authorizationContext, null, condition.getValueFrom()));
            } else {
                values.addAll(condition.getValues());
            }
            if (values.isEmpty()) {
                throw new BizRuleViolationException("CUSTOM_EXPRESSION IN 操作符至少需要一个值");
            }
            List<String> placeholders = new ArrayList<>();
            for (Object value : values) {
                placeholders.add(addParameter(parameters, value));
            }
            return column + " IN (" + String.join(", ", placeholders) + ")";
        }
        throw new BizRuleViolationException("CUSTOM_EXPRESSION 暂不支持操作符: " + operator);
    }

    protected Object resolveValue(UserAuthorizationContext authorizationContext, Object directValue, String valueFrom) {
        if (valueFrom == null || valueFrom.isBlank()) {
            return directValue;
        }
        String normalized = normalize(valueFrom);
        if ("CURRENTUSER.USERID".equals(normalized)) {
            return authorizationContext.getUserId();
        }
        if ("CURRENTUSER.USERNO".equals(normalized)) {
            return authorizationContext.getUserNo();
        }
        if ("CURRENTUSER.IDENTITY".equals(normalized)) {
            return authorizationContext.getIdentity();
        }
        throw new BizRuleViolationException("CUSTOM_EXPRESSION 不支持 valueFrom: " + valueFrom);
    }

    protected String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
