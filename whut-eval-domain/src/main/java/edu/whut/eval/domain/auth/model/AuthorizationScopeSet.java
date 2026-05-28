package edu.whut.eval.domain.auth.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 授权范围集合。
 * 表示某个权限码下最终可用的范围集合。
 */
public class AuthorizationScopeSet {

    private final String permissionCode;
    private final boolean granted;
    private final List<AuthorizationScope> scopes;

    public AuthorizationScopeSet(String permissionCode, boolean granted, List<AuthorizationScope> scopes) {
        this.permissionCode = permissionCode;
        this.granted = granted;
        this.scopes = scopes == null ? Collections.emptyList() : List.copyOf(scopes);
    }

    public static AuthorizationScopeSet denied(String permissionCode) {
        return new AuthorizationScopeSet(permissionCode, false, List.of());
    }

    public static AuthorizationScopeSet granted(String permissionCode, List<AuthorizationScope> scopes) {
        return new AuthorizationScopeSet(permissionCode, true, scopes);
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public boolean isGranted() {
        return granted;
    }

    public List<AuthorizationScope> getScopes() {
        return scopes;
    }

    public boolean hasScopes() {
        return !scopes.isEmpty();
    }

    public boolean allowsAll() {
        return containsScopeType("ALL");
    }

    public boolean allowsSelf() {
        return containsScopeType("SELF");
    }

    public boolean containsScopeType(String scopeType) {
        if (scopeType == null || scopeType.isBlank()) {
            return false;
        }
        String normalized = normalize(scopeType);
        return scopes.stream().anyMatch(scope -> normalized.equals(normalize(scope.getScopeType())));
    }

    public List<AuthorizationScope> findByScopeType(String scopeType) {
        if (scopeType == null || scopeType.isBlank()) {
            return List.of();
        }
        String normalized = normalize(scopeType);
        return scopes.stream()
                .filter(scope -> normalized.equals(normalize(scope.getScopeType())))
                .toList();
    }

    public Set<Long> getOrgUnitIds() {
        Set<Long> orgUnitIds = new LinkedHashSet<>();
        for (AuthorizationScope scope : scopes) {
            if (scope.getOrgUnitId() != null) {
                orgUnitIds.add(scope.getOrgUnitId());
            }
        }
        return Collections.unmodifiableSet(orgUnitIds);
    }

    public Set<String> getCategoryCodes() {
        Set<String> categoryCodes = new LinkedHashSet<>();
        for (AuthorizationScope scope : scopes) {
            if (scope.getCategoryCode() != null && !scope.getCategoryCode().isBlank()) {
                categoryCodes.add(scope.getCategoryCode());
            }
        }
        return Collections.unmodifiableSet(categoryCodes);
    }

    public Set<String> getItemCodes() {
        Set<String> itemCodes = new LinkedHashSet<>();
        for (AuthorizationScope scope : scopes) {
            if (scope.getItemCode() != null && !scope.getItemCode().isBlank()) {
                itemCodes.add(scope.getItemCode());
            }
        }
        return Collections.unmodifiableSet(itemCodes);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
