package edu.whut.eval.domain.preference.repository;

import edu.whut.eval.domain.preference.model.UserPreference;

import java.util.Optional;

/**
 * 用户偏好设置写入仓储。
 */
public interface UserPreferenceRepository {

    /**
     * 检查当前用户是否已经存在偏好设置，用于演示写入前冲突判断。
     */
    boolean existsByUserId(Long userId);

    /**
     * 按用户编号读取已存在的偏好设置。
     */
    Optional<UserPreference> findByUserId(Long userId);

    /**
     * 新增一条偏好设置记录。
     */
    UserPreference save(UserPreference userPreference);
}
