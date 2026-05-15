package edu.whut.eval.application.preference.command;

/**
 * 创建用户偏好设置命令。
 */
public class CreateUserPreferenceCommand {

    private final String preferredTheme;
    private final Boolean notificationsEnabled;

    public CreateUserPreferenceCommand(String preferredTheme, Boolean notificationsEnabled) {
        this.preferredTheme = preferredTheme;
        this.notificationsEnabled = notificationsEnabled;
    }

    public String getPreferredTheme() {
        return preferredTheme;
    }

    public Boolean getNotificationsEnabled() {
        return notificationsEnabled;
    }
}
