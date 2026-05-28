package edu.whut.eval.application.preference.service;

import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.preference.command.CreateUserPreferenceCommand;
import edu.whut.eval.application.preference.query.UserPreferenceView;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.preference.model.UserPreference;
import edu.whut.eval.domain.preference.repository.UserPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户偏好设置写入应用服务。
 * 该服务作为 P0-2 的最小写入样例，演示命令编排、冲突判断、事务边界和缓存失效。
 */
@Service
public class UserPreferenceCommandApplicationService {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserPreferenceCacheGateway userPreferenceCacheGateway;

    public UserPreferenceCommandApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                                   UserPreferenceRepository userPreferenceRepository,
                                                   UserPreferenceCacheGateway userPreferenceCacheGateway) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.userPreferenceRepository = userPreferenceRepository;
        this.userPreferenceCacheGateway = userPreferenceCacheGateway;
    }

    /**
     * 为当前登录用户创建偏好设置。
     * 同一用户只允许创建一次，重复创建会抛出冲突异常并映射为 409。
     */
    @Transactional
    public UserPreferenceView createCurrentUserPreference(CreateUserPreferenceCommand command) {
        UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
        Long userId = authorizationContext.getUserId();
        if (userPreferenceRepository.existsByUserId(userId)) {
            throw new ConflictException("当前用户已存在偏好设置，请改用更新接口");
        }

        UserPreference savedPreference = userPreferenceRepository.save(new UserPreference(
                null,
                userId,
                command.getPreferredTheme(),
                command.getNotificationsEnabled()
        ));
        userPreferenceCacheGateway.evictByUserId(userId);
        return new UserPreferenceView(
                savedPreference.id(),
                savedPreference.userId(),
                savedPreference.preferredTheme(),
                savedPreference.notificationsEnabled()
        );
    }
}
