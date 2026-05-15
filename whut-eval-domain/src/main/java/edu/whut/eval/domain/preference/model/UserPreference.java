package edu.whut.eval.domain.preference.model;

/**
 * 用户偏好设置聚合。
 * 该对象作为写入侧最小样例，只保留演示命令写入链路所需字段。
 */
public record UserPreference(Long id,
                             Long userId,
                             String preferredTheme,
                             Boolean notificationsEnabled) {
}
