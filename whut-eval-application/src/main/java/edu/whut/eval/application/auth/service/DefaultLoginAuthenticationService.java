package edu.whut.eval.application.auth.service;

import edu.whut.eval.application.auth.model.AuthenticatedUserSnapshot;
import edu.whut.eval.common.exception.AuthenticationFailedException;
import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.domain.iam.model.IamRoleAssignment;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.domain.iam.model.IamUserCredential;
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
 * 真实登录认证默认实现：校验密码后统一加载角色、权限和范围规则。
 */
@Service
public class DefaultLoginAuthenticationService implements LoginAuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultLoginAuthenticationService.class);

    private final IamUserQueryRepository iamUserQueryRepository;
    private final RoleAssignmentQueryRepository roleAssignmentQueryRepository;
    private final UserAuthorityQueryRepository userAuthorityQueryRepository;
    private final UserScopeRuleQueryRepository userScopeRuleQueryRepository;
    private final PasswordHashVerifier passwordHashVerifier;

    public DefaultLoginAuthenticationService(IamUserQueryRepository iamUserQueryRepository,
                                             RoleAssignmentQueryRepository roleAssignmentQueryRepository,
                                             UserAuthorityQueryRepository userAuthorityQueryRepository,
                                             UserScopeRuleQueryRepository userScopeRuleQueryRepository,
                                             PasswordHashVerifier passwordHashVerifier) {
        this.iamUserQueryRepository = iamUserQueryRepository;
        this.roleAssignmentQueryRepository = roleAssignmentQueryRepository;
        this.userAuthorityQueryRepository = userAuthorityQueryRepository;
        this.userScopeRuleQueryRepository = userScopeRuleQueryRepository;
        this.passwordHashVerifier = passwordHashVerifier;
    }

    /**
     * 登录成功后直接返回完整用户快照，避免控制器再次散落查询授权数据。
     */
    @Override
    public AuthenticatedUserSnapshot authenticate(String credential, String rawPassword) {
        String normalizedCredential = credential == null ? "" : credential.trim();
        AppLog.info(log, "security.auth.login.authenticate.started",
                "credential", normalizedCredential);

        IamUserCredential userCredential = iamUserQueryRepository.findCredentialByUserNo(normalizedCredential)
                .orElseThrow(() -> authenticationFailed("security.auth.login.user-not-found",
                        normalizedCredential,
                        "登录账号或密码错误"));
        validateActive(userCredential, normalizedCredential);
        validatePassword(rawPassword, userCredential, normalizedCredential);

        List<IamRoleAssignment> assignments = roleAssignmentQueryRepository.findActiveAssignmentsByUserId(userCredential.getId());
        Set<String> roles = assignments.stream()
                .map(IamRoleAssignment::roleCode)
                .filter(roleCode -> roleCode != null && !roleCode.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> authorities = resolveAuthorities(userCredential.getId(), normalizedCredential);
        List<IamScopeRule> scopeRules = resolveScopeRules(userCredential.getId(), normalizedCredential);
        String identity = resolveIdentity(roles);

        AppLog.info(log, "security.auth.login.authenticate.succeeded",
                "userId", userCredential.getId(),
                "userNo", userCredential.getUserNo(),
                "identity", identity,
                "roleCount", roles.size(),
                "authorityCount", authorities.size(),
                "scopeRuleCount", scopeRules.size());
        return new AuthenticatedUserSnapshot(
                userCredential.getId(),
                userCredential.getUserNo(),
                userCredential.getUserName(),
                identity,
                Set.copyOf(roles),
                Set.copyOf(authorities),
                List.copyOf(scopeRules)
        );
    }

    /**
     * 登录阶段只接受 ACTIVE 用户，避免已禁用账号继续换取新 token。
     */
    private void validateActive(IamUserCredential userCredential, String credential) {
        if (userCredential.getStatus() != null && !"ACTIVE".equalsIgnoreCase(userCredential.getStatus())) {
            AppLog.warn(log, "security.auth.login.user-inactive",
                    "credential", credential,
                    "userId", userCredential.getId(),
                    "userNo", userCredential.getUserNo(),
                    "status", userCredential.getStatus());
            throw new AuthenticationFailedException("登录账号或密码错误");
        }
    }

    /**
     * 把密码校验统一收口在摘要校验器中，便于后续替换口令算法实现。
     */
    private void validatePassword(String rawPassword, IamUserCredential userCredential, String credential) {
        if (!passwordHashVerifier.matches(rawPassword, userCredential.getPasswordHash())) {
            AppLog.warn(log, "security.auth.login.password-mismatch",
                    "credential", credential,
                    "userId", userCredential.getId(),
                    "userNo", userCredential.getUserNo());
            throw new AuthenticationFailedException("登录账号或密码错误");
        }
    }

    /**
     * 登录时同步装配权限快照，保证 access token 的权限声明来自最新数据库状态。
     */
    private Set<String> resolveAuthorities(Long userId, String credential) {
        Set<String> authorities = userAuthorityQueryRepository.findActivePermissionCodesByUserId(userId);
        if (authorities.isEmpty()) {
            AppLog.warn(log, "security.auth.login.authorities-empty",
                    "credential", credential,
                    "userId", userId);
            return Set.of();
        }
        AppLog.info(log, "security.auth.login.authorities-loaded",
                "credential", credential,
                "userId", userId,
                "authorityCount", authorities.size());
        return Set.copyOf(authorities);
    }

    /**
     * 登录时同步装配范围规则，便于后续直接落入 CurrentUser 和 refresh 基线模型。
     */
    private List<IamScopeRule> resolveScopeRules(Long userId, String credential) {
        List<IamScopeRule> scopeRules = userScopeRuleQueryRepository.findActiveScopeRulesByUserId(userId);
        if (scopeRules.isEmpty()) {
            AppLog.info(log, "security.auth.login.scope-rules-empty",
                    "credential", credential,
                    "userId", userId);
            return List.of();
        }
        AppLog.info(log, "security.auth.login.scope-rules-loaded",
                "credential", credential,
                "userId", userId,
                "scopeRuleCount", scopeRules.size());
        return List.copyOf(scopeRules);
    }

    /**
     * 现阶段 identity 仍保留单值字段，这里用最小兼容策略从多角色集合中推导。
     */
    private String resolveIdentity(Set<String> roles) {
        if (roles.contains("student")) {
            return "student";
        }
        return roles.stream().sorted().findFirst().orElse("user");
    }

    /**
     * 统一记录失败事件，避免不同登录失败分支的日志格式漂移。
     */
    private AuthenticationFailedException authenticationFailed(String event, String credential, String message) {
        AppLog.warn(log, event, "credential", credential);
        return new AuthenticationFailedException(message);
    }
}
