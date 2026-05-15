package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.domain.preference.model.UserPreference;
import edu.whut.eval.domain.preference.repository.UserPreferenceRepository;
import edu.whut.eval.infra.persistence.dataobject.UserPreferenceDO;
import edu.whut.eval.infra.persistence.mapper.UserPreferenceMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户偏好设置仓储的 MyBatis 实现。
 */
@Repository
public class MybatisPlusUserPreferenceRepository implements UserPreferenceRepository {

    private final UserPreferenceMapper userPreferenceMapper;

    public MybatisPlusUserPreferenceRepository(UserPreferenceMapper userPreferenceMapper) {
        this.userPreferenceMapper = userPreferenceMapper;
    }

    @Override
    public boolean existsByUserId(Long userId) {
        return userPreferenceMapper.existsByUserId(userId);
    }

    @Override
    public Optional<UserPreference> findByUserId(Long userId) {
        return Optional.ofNullable(userPreferenceMapper.selectByUserId(userId))
                .map(this::toDomain);
    }

    @Override
    public UserPreference save(UserPreference userPreference) {
        UserPreferenceDO userPreferenceDO = new UserPreferenceDO();
        userPreferenceDO.setUserId(userPreference.userId());
        userPreferenceDO.setPreferredTheme(userPreference.preferredTheme());
        userPreferenceDO.setNotificationsEnabled(userPreference.notificationsEnabled());
        userPreferenceMapper.insert(userPreferenceDO);
        return toDomain(userPreferenceDO);
    }

    private UserPreference toDomain(UserPreferenceDO userPreferenceDO) {
        return new UserPreference(
                userPreferenceDO.getId(),
                userPreferenceDO.getUserId(),
                userPreferenceDO.getPreferredTheme(),
                userPreferenceDO.getNotificationsEnabled()
        );
    }
}
