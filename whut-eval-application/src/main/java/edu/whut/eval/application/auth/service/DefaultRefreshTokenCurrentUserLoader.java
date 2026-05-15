package edu.whut.eval.application.auth.service;

import edu.whut.eval.application.auth.model.AuthenticatedUserSnapshot;
import edu.whut.eval.application.auth.model.RefreshTokenReloadContext;
import edu.whut.eval.common.exception.AuthenticationFailedException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.domain.iam.model.IamRoleAssignment;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.iam.repository.RoleAssignmentQueryRepository;
import edu.whut.eval.domain.iam.repository.UserAuthorityQueryRepository;
import edu.whut.eval.domain.iam.repository.UserScopeRuleQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * refresh token 重载默认实现：基于最小身份重新查库，恢复完整用户授权快照。
 */
@Service
public class DefaultRefreshTokenCurrentUserLoader implements RefreshTokenCurrentUserLoader {

    private static final Logger log = LoggerFactory.getLogger(DefaultRefreshTokenCurrentUserLoader.class);

    private final IamUserQueryRepository iamUserQueryRepository;
    private final RoleAssignmentQueryRepository roleAssignmentQueryRepository;
    private final UserAuthorityQueryRepository userAuthorityQueryRepository;
    private final UserScopeRuleQueryRepository userScopeRuleQueryRepository;

    public DefaultRefreshTokenCurrentUserLoader(IamUserQueryRepository iamUserQueryRepository,
                                                RoleAssignmentQueryRepository roleAssignmentQueryRepository,
                                                UserAuthorityQueryRepository userAuthorityQueryRepository,
                                                UserScopeRuleQueryRepository userScopeRuleQueryRepository) {
        this.iamUserQueryRepository = iamUserQueryRepository;
        this.roleAssignmentQueryRepository = roleAssignmentQueryRepository;
        this.userAuthorityQueryRepository = userAuthorityQueryRepository;
        this.userScopeRuleQueryRepository = userScopeRuleQueryRepository;
    }

    /**
     * refresh 场景不信任 token 里的权限快照，必须重新查库校验用户状态和授权数据。
     */
    @Override
    public AuthenticatedUserSnapshot load(RefreshTokenReloadContext context) {
        AppLog.info(log, "security.auth.refresh.loader.started",
                "userId", context.userId(),
                "userNo", context.userNo(),
                "identity", context.identity());

        IamUser user = iamUserQueryRepository.findById(context.userId())
                .orElseThrow(() -> new ResourceNotFoundException("刷新用户不存在: " + context.userId()));
        validateSubject(user, context);

        List<IamRoleAssignment> assignments = roleAssignmentQueryRepository.findActiveAssignmentsByUserId(user.id());
        Set<String> roles = assignments.stream()
                .map(IamRoleAssignment::roleCode)
                .filter(roleCode -> roleCode != null && !roleCode.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> authorities = resolveAuthorities(user.id(), context);
        List<IamScopeRule> scopeRules = resolveScopeRules(user.id(), context);

        AppLog.info(log, "security.auth.refresh.loader.succeeded",
                "userId", user.id(),
                "userNo", user.userNo(),
                "identity", context.identity(),
                "roleCount", roles.size(),
                "authorityCount", authorities.size(),
                "scopeRuleCount", scopeRules.size());
        return new AuthenticatedUserSnapshot(
                user.id(),
                user.userNo(),
                user.userName(),
                context.identity(),
                Set.copyOf(roles),
                Set.copyOf(authorities),
                List.copyOf(scopeRules)
        );
    }

    /**
     * 校验 refresh token 主体与数据库用户是否仍然一致，防止使用过期或伪造主体换发 token。
     */
    private void validateSubject(IamUser user, RefreshTokenReloadContext context) {
        if (!user.userNo().equals(context.userNo())) {
            AppLog.warn(log, "security.auth.refresh.loader.user-no-mismatch",
                    "expected", user.userNo(),
                    "actual", context.userNo(),
                    "userId", context.userId());
            throw new AuthenticationFailedException("refresh token 用户标识不一致");
        }
        if (user.status() != null && !"ACTIVE".equalsIgnoreCase(user.status())) {
            AppLog.warn(log, "security.auth.refresh.loader.user-inactive",
                    "userId", user.id(),
                    "userNo", user.userNo(),
                    "status", user.status());
            throw new AuthenticationFailedException("refresh token 对应用户已失效");
        }
    }

    /**
     * 重新查询最新权限集合，保证 refresh 后拿到的是数据库当前有效权限。
     */
    private Set<String> resolveAuthorities(Long userId, RefreshTokenReloadContext context) {
        Set<String> authorities = userAuthorityQueryRepository.findActivePermissionCodesByUserId(userId);
        if (authorities.isEmpty()) {
            AppLog.warn(log, "security.auth.refresh.loader.authorities-empty",
                    "userId", context.userId(),
                    "userNo", context.userNo(),
                    "identity", context.identity());
            return Set.of();
        }
        AppLog.info(log, "security.auth.refresh.loader.authorities-loaded",
                "userId", context.userId(),
                "userNo", context.userNo(),
                "identity", context.identity(),
                "authorityCount", authorities.size());
        return Set.copyOf(authorities);
    }

    /**
     * 重新查询最新范围规则，保证 refresh 后 scopeRules 与当前角色/权限状态一致。
     */
    private List<IamScopeRule> resolveScopeRules(Long userId, RefreshTokenReloadContext context) {
        List<IamScopeRule> scopeRules = userScopeRuleQueryRepository.findActiveScopeRulesByUserId(userId);
        if (scopeRules.isEmpty()) {
            AppLog.info(log, "security.auth.refresh.loader.scope-rules-empty",
                    "userId", context.userId(),
                    "userNo", context.userNo(),
                    "identity", context.identity());
            return List.of();
        }
        AppLog.info(log, "security.auth.refresh.loader.scope-rules-loaded",
                "userId", context.userId(),
                "userNo", context.userNo(),
                "identity", context.identity(),
                "scopeRuleCount", scopeRules.size());
        return List.copyOf(scopeRules);
    }
}
