package edu.whut.eval.infra.security.context;

import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContextLoadRequest;
import edu.whut.eval.application.auth.service.UserAuthorizationContextLoader;
import edu.whut.eval.common.exception.AuthenticationFailedException;
import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.domain.iam.repository.UserAuthorityQueryRepository;
import edu.whut.eval.domain.iam.repository.UserScopeRuleQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class RepositoryUserAuthorizationContextLoader implements UserAuthorizationContextLoader {

    private static final Logger log = LoggerFactory.getLogger(RepositoryUserAuthorizationContextLoader.class);

    private final UserAuthorityQueryRepository userAuthorityQueryRepository;
    private final UserScopeRuleQueryRepository userScopeRuleQueryRepository;

    public RepositoryUserAuthorizationContextLoader(UserAuthorityQueryRepository userAuthorityQueryRepository,
                                                    UserScopeRuleQueryRepository userScopeRuleQueryRepository) {
        this.userAuthorityQueryRepository = userAuthorityQueryRepository;
        this.userScopeRuleQueryRepository = userScopeRuleQueryRepository;
    }

    @Override
    public UserAuthorizationContext load(UserAuthorizationContextLoadRequest request) {
        validateRequest(request);
        AppLog.info(log, "security.authorization.context.load.started",
                "userId", request.getUserId(),
                "userNo", request.getUserNo(),
                "identity", request.getIdentity());

        Set<String> authorities = resolveAuthorities(request);
        List<IamScopeRule> scopeRules = resolveScopeRules(request);

        AppLog.info(log, "security.authorization.context.load.succeeded",
                "userId", request.getUserId(),
                "userNo", request.getUserNo(),
                "identity", request.getIdentity(),
                "roleCount", request.getRoles().size(),
                "authorityCount", authorities.size(),
                "scopeRuleCount", scopeRules.size());
        return new UserAuthorizationContext(
                request.getUserId(),
                request.getUserNo(),
                request.getUserName(),
                request.getIdentity(),
                request.getRoles(),
                authorities,
                scopeRules
        );
    }

    private void validateRequest(UserAuthorizationContextLoadRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new AuthenticationFailedException("当前认证上下文缺少 userId，无法补齐授权信息");
        }
    }

    private Set<String> resolveAuthorities(UserAuthorizationContextLoadRequest request) {
        Set<String> authorities = userAuthorityQueryRepository.findActivePermissionCodesByUserId(request.getUserId());
        if (authorities.isEmpty()) {
            AppLog.warn(log, "security.authorization.context.load.authorities-empty",
                    "userId", request.getUserId(),
                    "userNo", request.getUserNo(),
                    "identity", request.getIdentity());
            return Set.of();
        }
        AppLog.info(log, "security.authorization.context.load.authorities-loaded",
                "userId", request.getUserId(),
                "userNo", request.getUserNo(),
                "identity", request.getIdentity(),
                "authorityCount", authorities.size());
        return Set.copyOf(authorities);
    }

    private List<IamScopeRule> resolveScopeRules(UserAuthorizationContextLoadRequest request) {
        List<IamScopeRule> scopeRules = userScopeRuleQueryRepository.findActiveScopeRulesByUserId(request.getUserId());
        if (scopeRules.isEmpty()) {
            AppLog.info(log, "security.authorization.context.load.scope-rules-empty",
                    "userId", request.getUserId(),
                    "userNo", request.getUserNo(),
                    "identity", request.getIdentity());
            return List.of();
        }
        AppLog.info(log, "security.authorization.context.load.scope-rules-loaded",
                "userId", request.getUserId(),
                "userNo", request.getUserNo(),
                "identity", request.getIdentity(),
                "scopeRuleCount", scopeRules.size());
        return List.copyOf(scopeRules);
    }
}
