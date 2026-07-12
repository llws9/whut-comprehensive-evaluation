package edu.whut.eval.infra.security.sql;

import java.util.regex.Pattern;

public final class D11ScopeSqlShape {

    public static final String CLASS_ALIAS_PLACEHOLDER = "__D11_CLASS_ALIAS__";

    private D11ScopeSqlShape() {
    }

    public static String normalize(String expression) {
        return expression == null ? "" : expression.strip().replaceAll("\\s+", " ");
    }

    public static String orgUnitFragment(String inSql) {
        return CLASS_ALIAS_PLACEHOLDER + ".id IN (" + inSql + ")";
    }

    public static String orgSubtreeFragment(String inSql) {
        return """
                EXISTS (
                  SELECT 1
                  FROM org_unit root_ou
                  WHERE root_ou.id IN (%s)
                    AND root_ou.status = 'ACTIVE'
                    AND root_ou.path IS NOT NULL
                    AND root_ou.path <> ''
                    AND root_ou.path LIKE '/%%'
                    AND root_ou.path NOT LIKE '%%/'
                    AND LOCATE('%%', root_ou.path) = 0
                    AND LOCATE('_', root_ou.path) = 0
                    AND __D11_CLASS_ALIAS__.path IS NOT NULL
                    AND __D11_CLASS_ALIAS__.path <> ''
                    AND __D11_CLASS_ALIAS__.path LIKE '/%%'
                    AND __D11_CLASS_ALIAS__.path NOT LIKE '%%/'
                    AND LOCATE('%%', __D11_CLASS_ALIAS__.path) = 0
                    AND LOCATE('_', __D11_CLASS_ALIAS__.path) = 0
                    AND (
                      __D11_CLASS_ALIAS__.path = root_ou.path
                      OR __D11_CLASS_ALIAS__.path LIKE CONCAT(root_ou.path, '/%%')
                    )
                )
                """.formatted(inSql).strip();
    }

    public static boolean isAllowedScopeExpression(String normalized) {
        if ("1 = 0".equals(normalized) || "1 = 1".equals(normalized)) {
            return true;
        }
        String orgUnitFragmentPattern = fragmentPattern(
                orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}"),
                "#{scopeFragment.parameters.d11OrgUnit0}",
                parameterListPattern("d11OrgUnit")
        );
        String subtreeFragmentPattern = fragmentPattern(
                orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}"),
                "#{scopeFragment.parameters.d11Subtree0}",
                parameterListPattern("d11Subtree")
        );
        String orgUnitOnlyPattern = "\\( ?" + orgUnitFragmentPattern + " ?\\)";
        String subtreeOnlyPattern = "\\( ?" + subtreeFragmentPattern + " ?\\)";
        String orgThenSubtreePattern = "\\( ?" + orgUnitFragmentPattern + " OR " + subtreeFragmentPattern + " ?\\)";
        String subtreeThenOrgPattern = "\\( ?" + subtreeFragmentPattern + " OR " + orgUnitFragmentPattern + " ?\\)";
        return Pattern.matches(orgUnitOnlyPattern, normalized)
                || Pattern.matches(subtreeOnlyPattern, normalized)
                || Pattern.matches(orgThenSubtreePattern, normalized)
                || Pattern.matches(subtreeThenOrgPattern, normalized);
    }

    public static void assertGeneratedFragmentsSelfValidateForD11() {
        String singleOrgUnit = "(" + orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}") + ")";
        String multiOrgUnit = "(" + orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}, #{scopeFragment.parameters.d11OrgUnit1}") + ")";
        String singleSubtree = "(" + orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}") + ")";
        String multiSubtree = "(" + orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}, #{scopeFragment.parameters.d11Subtree1}") + ")";
        String orgThenSubtree = "(" + orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}")
                + " OR " + orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}") + ")";
        String subtreeThenOrg = "(" + orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}")
                + " OR " + orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}") + ")";
        String multiOrgThenSingleSubtree = "(" + orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}, #{scopeFragment.parameters.d11OrgUnit1}")
                + " OR " + orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}") + ")";
        String singleOrgThenMultiSubtree = "(" + orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}")
                + " OR " + orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}, #{scopeFragment.parameters.d11Subtree1}") + ")";
        String multiOrgThenMultiSubtree = "(" + orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}, #{scopeFragment.parameters.d11OrgUnit1}")
                + " OR " + orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}, #{scopeFragment.parameters.d11Subtree1}") + ")";
        if (!isAllowedScopeExpression(normalize(singleOrgUnit))
                || !isAllowedScopeExpression(normalize(multiOrgUnit))
                || !isAllowedScopeExpression(normalize(singleSubtree))
                || !isAllowedScopeExpression(normalize(multiSubtree))
                || !isAllowedScopeExpression(normalize(orgThenSubtree))
                || !isAllowedScopeExpression(normalize(subtreeThenOrg))
                || !isAllowedScopeExpression(normalize(multiOrgThenSingleSubtree))
                || !isAllowedScopeExpression(normalize(singleOrgThenMultiSubtree))
                || !isAllowedScopeExpression(normalize(multiOrgThenMultiSubtree))) {
            throw new IllegalStateException("Generated D-11 scope SQL must match whitelist");
        }
    }

    private static String parameterListPattern(String prefix) {
        String parameterPattern = "#\\{scopeFragment\\.parameters\\." + prefix + "\\d+\\}";
        return parameterPattern + "(, " + parameterPattern + ")*";
    }

    private static String fragmentPattern(String generatedFragment,
                                          String samplePlaceholder,
                                          String placeholderPattern) {
        String normalized = normalize(generatedFragment);
        String[] parts = normalized.split(Pattern.quote(samplePlaceholder), -1);
        if (parts.length != 2) {
            throw new IllegalStateException("D-11 scope fragment must contain one sample placeholder");
        }
        return Pattern.quote(parts[0]) + placeholderPattern + Pattern.quote(parts[1]);
    }
}
