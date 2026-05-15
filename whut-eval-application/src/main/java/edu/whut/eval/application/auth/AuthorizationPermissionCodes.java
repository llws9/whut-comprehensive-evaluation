package edu.whut.eval.application.auth;

/**
 * 统一维护当前应用层显式使用的权限码常量，避免 controller、service、测试之间散落字符串字面量。
 */
public final class AuthorizationPermissionCodes {

    public static final String APPLICATION_VIEW_SELF = "application.view.self";
    public static final String APPLICATION_REVIEW = "application.review";
    public static final String SCORE_VIEW_SELF = "score.view.self";
    public static final String SCORE_VIEW_ASSIGNED = "score.view.assigned";

    private AuthorizationPermissionCodes() {
    }
}
