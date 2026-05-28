package edu.whut.eval.app.security;

import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContextLoadRequest;
import edu.whut.eval.common.exception.AuthenticationFailedException;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.domain.iam.repository.UserAuthorityQueryRepository;
import edu.whut.eval.domain.iam.repository.UserScopeRuleQueryRepository;
import edu.whut.eval.infra.security.context.RepositoryUserAuthorizationContextLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RepositoryUserAuthorizationContextLoaderTest {

    @Mock
    private UserAuthorityQueryRepository userAuthorityQueryRepository;

    @Mock
    private UserScopeRuleQueryRepository userScopeRuleQueryRepository;

    @InjectMocks
    private RepositoryUserAuthorizationContextLoader loader;

    @Test
    void shouldLoadAuthoritiesAndScopeRulesByUserId() {
        given(userAuthorityQueryRepository.findActivePermissionCodesByUserId(1001L)).willReturn(Set.of(
                "application.view.self",
                "score.view.self"
        ));
        given(userScopeRuleQueryRepository.findActiveScopeRulesByUserId(1001L)).willReturn(List.of(
                new IamScopeRule(1L, "application.view.self", "SELF", null, null, null, null, 10, "ACTIVE"),
                new IamScopeRule(2L, "score.view.self", "ORG_UNIT", 3001L, null, null, null, 20, "ACTIVE")
        ));

        UserAuthorizationContext context = loader.load(new UserAuthorizationContextLoadRequest(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student", "class-monitor")
        ));

        assertThat(context.getUserId()).isEqualTo(1001L);
        assertThat(context.getUserNo()).isEqualTo("2024305999");
        assertThat(context.getUserName()).isEqualTo("Test User");
        assertThat(context.getIdentity()).isEqualTo("student");
        assertThat(context.getRoles()).containsExactlyInAnyOrder("student", "class-monitor");
        assertThat(context.getAuthorities()).containsExactlyInAnyOrder("application.view.self", "score.view.self");
        assertThat(context.getScopeRules()).hasSize(2);
        assertThat(context.findScopeRulesByPermissionCode("application.view.self")).hasSize(1);
        assertThat(context.findScopeRulesByPermissionCode("score.view.self")).hasSize(1);
    }

    @Test
    void shouldRejectWhenUserIdIsMissing() {
        assertThatThrownBy(() -> loader.load(new UserAuthorizationContextLoadRequest(
                null,
                "2024305999",
                "Test User",
                "student",
                Set.of("student")
        )))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("当前认证上下文缺少 userId，无法补齐授权信息");
    }
}
