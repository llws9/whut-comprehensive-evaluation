package edu.whut.eval.domain.iam.model;

import java.time.LocalDateTime;

/**
 * 统一角色分配在当前时刻的语义状态判定。
 */
public final class RoleAssignmentCurrentStatusResolver {

    private RoleAssignmentCurrentStatusResolver() {
    }

    public static String resolve(String persistedStatus,
                                 LocalDateTime effectiveFrom,
                                 LocalDateTime effectiveTo,
                                 LocalDateTime now) {
        if (!"ACTIVE".equals(persistedStatus)) {
            return persistedStatus;
        }
        if (effectiveFrom != null && effectiveFrom.isAfter(now)) {
            return "INACTIVE";
        }
        if (effectiveTo != null && !effectiveTo.isAfter(now)) {
            return "EXPIRED";
        }
        return persistedStatus;
    }
}
