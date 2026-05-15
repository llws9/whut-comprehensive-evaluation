package edu.whut.eval.application.preference.query;

/**
 * 用户偏好设置写接口返回视图。
 */
public class UserPreferenceView {

    private final Long id;
    private final Long userId;
    private final String preferredTheme;
    private final Boolean notificationsEnabled;

    public UserPreferenceView(Long id, Long userId, String preferredTheme, Boolean notificationsEnabled) {
        this.id = id;
        this.userId = userId;
        this.preferredTheme = preferredTheme;
        this.notificationsEnabled = notificationsEnabled;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getPreferredTheme() {
        return preferredTheme;
    }

    public Boolean getNotificationsEnabled() {
        return notificationsEnabled;
    }
}
