package edu.whut.eval.interfaces.student.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建用户偏好设置请求。
 */
public class CreateUserPreferenceRequest {

    @NotBlank(message = "preferredTheme 不能为空")
    private String preferredTheme;

    @NotNull(message = "notificationsEnabled 不能为空")
    private Boolean notificationsEnabled;

    public String getPreferredTheme() {
        return preferredTheme;
    }

    public void setPreferredTheme(String preferredTheme) {
        this.preferredTheme = preferredTheme;
    }

    public Boolean getNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(Boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }
}
