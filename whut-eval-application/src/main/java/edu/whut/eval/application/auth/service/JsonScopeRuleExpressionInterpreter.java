package edu.whut.eval.application.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.auth.model.ScopeExpression;
import edu.whut.eval.application.auth.model.ScopeExpressionCondition;
import edu.whut.eval.application.auth.model.ScopeResourceContext;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.common.exception.BizRuleViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 受控 JSON DSL 解释器，负责解析并执行 CUSTOM_EXPRESSION。
 */
@Service
public class JsonScopeRuleExpressionInterpreter implements ScopeRuleExpressionInterpreter {

    private final ObjectMapper objectMapper;

    public JsonScopeRuleExpressionInterpreter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析并校验 DSL 结构，确保后续运行时匹配只处理受控表达式模型。
     */
    @Override
    public ScopeExpression parse(String expressionJson) {
        if (expressionJson == null || expressionJson.isBlank()) {
            throw new BizRuleViolationException("CUSTOM_EXPRESSION 不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(expressionJson);
            JsonNode allOfNode = root.get("allOf");
            if (allOfNode == null || !allOfNode.isArray() || allOfNode.isEmpty()) {
                throw new BizRuleViolationException("CUSTOM_EXPRESSION 必须包含非空 allOf 条件数组");
            }
            List<ScopeExpressionCondition> conditions = new ArrayList<>();
            for (JsonNode conditionNode : allOfNode) {
                conditions.add(parseCondition(conditionNode));
            }
            return new ScopeExpression(conditions);
        } catch (BizRuleViolationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizRuleViolationException("CUSTOM_EXPRESSION JSON 解析失败");
        }
    }

    /**
     * 逐条执行 allOf 条件，只有全部命中才视为 CUSTOM_EXPRESSION 命中。
     */
    @Override
    public boolean matches(UserAuthorizationContext authorizationContext,
                           String expressionJson,
                           ScopeResourceContext resourceContext) {
        ScopeExpression expression = parse(expressionJson);
        for (ScopeExpressionCondition condition : expression.getAllOf()) {
            if (!matchesCondition(authorizationContext, resourceContext, condition)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 把单个 JSON 条件节点转换成统一条件模型，并在这里收口字段合法性校验。
     */
    private ScopeExpressionCondition parseCondition(JsonNode conditionNode) {
        String field = readRequiredText(conditionNode, "field");
        String operator = normalize(readRequiredText(conditionNode, "operator"));
        String valueFrom = readOptionalText(conditionNode, "valueFrom");
        JsonNode valueNode = conditionNode.get("value");
        JsonNode valuesNode = conditionNode.get("values");
        List<Object> values = new ArrayList<>();
        if (valuesNode != null) {
            if (!valuesNode.isArray() || valuesNode.isEmpty()) {
                throw new BizRuleViolationException("CUSTOM_EXPRESSION values 必须是非空数组");
            }
            for (JsonNode element : valuesNode) {
                values.add(toJavaValue(element));
            }
        }
        return new ScopeExpressionCondition(field, operator, toJavaValue(valueNode), values, valueFrom);
    }

    /**
     * 当前 DSL 仅支持 EQ 和 IN，两类比较都统一走期望值解析逻辑。
     */
    private boolean matchesCondition(UserAuthorizationContext authorizationContext,
                                     ScopeResourceContext resourceContext,
                                     ScopeExpressionCondition condition) {
        Object actual = resourceContext.getFieldValue(condition.getField());
        String operator = normalize(condition.getOperator());
        if ("EQ".equals(operator)) {
            Object expected = resolveExpectedValue(authorizationContext, condition);
            return valuesEqual(actual, expected);
        }
        if ("IN".equals(operator)) {
            List<Object> expectedValues = resolveExpectedValues(authorizationContext, condition);
            for (Object expectedValue : expectedValues) {
                if (valuesEqual(actual, expectedValue)) {
                    return true;
                }
            }
            return false;
        }
        throw new BizRuleViolationException("CUSTOM_EXPRESSION 暂不支持操作符: " + operator);
    }

    /**
     * 单值比较优先支持 valueFrom 动态取值，否则回退到表达式里的固定 value。
     */
    private Object resolveExpectedValue(UserAuthorizationContext authorizationContext,
                                        ScopeExpressionCondition condition) {
        if (condition.getValueFrom() != null && !condition.getValueFrom().isBlank()) {
            return resolveValueFrom(authorizationContext, condition.getValueFrom());
        }
        return condition.getValue();
    }

    /**
     * IN 比较同样允许通过 valueFrom 动态提供单个比较值。
     */
    private List<Object> resolveExpectedValues(UserAuthorizationContext authorizationContext,
                                               ScopeExpressionCondition condition) {
        if (condition.getValueFrom() != null && !condition.getValueFrom().isBlank()) {
            return List.of(resolveValueFrom(authorizationContext, condition.getValueFrom()));
        }
        return condition.getValues();
    }

    /**
     * 严格限制 valueFrom 只允许访问当前用户上下文中的受控字段。
     */
    private Object resolveValueFrom(UserAuthorizationContext authorizationContext, String valueFrom) {
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

    /**
     * 把 JsonNode 转成尽量保留语义的 Java 值，便于后续统一比较。
     */
    private Object toJavaValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }

    /**
     * 数值按 BigDecimal 比较，其他类型按忽略大小写的字符串语义比较。
     */
    private boolean valuesEqual(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return false;
        }
        if (actual instanceof Number && expected instanceof Number) {
            Number actualNumber = (Number) actual;
            Number expectedNumber = (Number) expected;
            return new BigDecimal(actualNumber.toString()).compareTo(new BigDecimal(expectedNumber.toString())) == 0;
        }
        return String.valueOf(actual).trim().equalsIgnoreCase(String.valueOf(expected).trim());
    }

    /**
     * 读取必填文本字段，缺失时直接抛出 DSL 结构异常。
     */
    private String readRequiredText(JsonNode node, String fieldName) {
        String value = readOptionalText(node, fieldName);
        if (value == null || value.isBlank()) {
            throw new BizRuleViolationException("CUSTOM_EXPRESSION 缺少字段: " + fieldName);
        }
        return value;
    }

    /**
     * 读取可选文本字段，允许缺失或显式 null。
     */
    private String readOptionalText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * 统一标准化 DSL 关键字，避免大小写差异导致表达式失效。
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
