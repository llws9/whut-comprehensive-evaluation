package edu.whut.eval.application.auth;

/**
 * 统一维护当前应用层显式使用的权限码常量，避免 controller、service、测试之间散落字符串字面量。
 * 权限清单基于 A组蓝图 identity-access-implementation-blueprint.md 第 3.1 节。
 */
public final class AuthorizationPermissionCodes {

    // 申请相关权限
    public static final String APPLICATION_SUBMIT = "application.submit";
    public static final String APPLICATION_UPDATE = "application.update";
    public static final String APPLICATION_DELETE = "application.delete";
    public static final String APPLICATION_VIEW_SELF = "application.view.self";
    public static final String APPLICATION_VIEW_ASSIGNED = "application.view.assigned";
    public static final String APPLICATION_REVIEW = "application.review";

    // 审核任务权限
    public static final String REVIEW_TASK_VIEW = "review.task.view";
    public static final String REVIEW_TASK_ASSIGN = "review.task.assign";

    // 成绩相关权限
    public static final String SCORE_VIEW_SELF = "score.view.self";
    public static final String SCORE_VIEW_ASSIGNED = "score.view.assigned";
    public static final String SCORE_EXPORT_ASSIGNED = "score.export.assigned";
    public static final String SCORE_CONFIRM_ASSIGNED = "score.confirm.assigned";
    public static final String SCORE_IMPORT = "score.import";

    // 最终成绩权限
    public static final String FINAL_SUBMIT_SELF = "final.submit.self";
    public static final String FINAL_VIEW_SELF = "final.view.self";

    // 用户管理权限
    public static final String USER_MANAGE = "user.manage";
    public static final String USER_IMPORT = "user.import";

    // 角色管理权限
    public static final String ROLE_MANAGE = "role.manage";
    public static final String ASSIGNMENT_MANAGE = "assignment.manage";
    public static final String PERMISSION_MANAGE = "permission.manage";

    // 组织管理权限
    public static final String ORG_MANAGE = "org.manage";

    // 申请项目定义管理权限
    public static final String EVALUATION_ITEM_MANAGE = "evaluation.item.manage";

    // 平台规则权限
    public static final String PLATFORM_RULE_MANAGE = "platform.rule.manage";
    public static final String PLATFORM_SWITCH_MANAGE = "platform.switch.manage";

    // 公共附件池权限
    public static final String ATTACHMENT_POOL_PUBLISH = "attachment.pool.publish";
    public static final String ATTACHMENT_POOL_OFFLINE = "attachment.pool.offline";

    private AuthorizationPermissionCodes() {
    }
}
