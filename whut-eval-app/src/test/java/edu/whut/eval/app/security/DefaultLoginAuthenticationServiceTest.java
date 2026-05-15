package edu.whut.eval.app.security;

import edu.whut.eval.application.auth.model.AuthenticatedUserSnapshot;
import edu.whut.eval.application.auth.service.DefaultLoginAuthenticationService;
import edu.whut.eval.application.auth.service.PasswordHashVerifier;
import edu.whut.eval.common.exception.AuthenticationFailedException;
import edu.whut.eval.domain.iam.model.IamRoleAssignment;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.domain.iam.model.IamUserCredential;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.iam.repository.RoleAssignmentQueryRepository;
import edu.whut.eval.domain.iam.repository.UserAuthorityQueryRepository;
import edu.whut.eval.domain.iam.repository.UserScopeRuleQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DefaultLoginAuthenticationServiceTest {

    @Mock
    private IamUserQueryRepository iamUserQueryRepository;

    @Mock
    private RoleAssignmentQueryRepository roleAssignmentQueryRepository;

    @Mock
    private UserAuthorityQueryRepository userAuthorityQueryRepository;

    @Mock
    private UserScopeRuleQueryRepository userScopeRuleQueryRepository;

    @Mock
    private PasswordHashVerifier passwordHashVerifier;

    @InjectMocks
    private DefaultLoginAuthenticationService loginAuthenticationService;

    @Test
    void shouldAuthenticateAndReturnSnapshot() {
        IamUserCredential credential = new IamUserCredential(
                1001L,
                "2024305999",
                "Test User",
                "hash",
                "ACTIVE"
        );
        given(iamUserQueryRepository.findCredentialByUserNo("2024305999")).willReturn(Optional.of(credential));
        given(passwordHashVerifier.matches("secret", "hash")).willReturn(true);
        given(roleAssignmentQueryRepository.findActiveAssignmentsByUserId(1001L)).willReturn(List.of(
                new IamRoleAssignment(11L, 21L, "student", "Student", null, "ACTIVE"),
                new IamRoleAssignment(12L, 22L, "class-monitor", "Class Monitor", null, "ACTIVE")
        ));
        given(userAuthorityQueryRepository.findActivePermissionCodesByUserId(1001L)).willReturn(Set.of(
                "application.view.self",
                "application.view.assigned"
        ));
        given(userScopeRuleQueryRepository.findActiveScopeRulesByUserId(1001L)).willReturn(List.of(
                new IamScopeRule(101L, "application.view.self", "SELF", null, null, null, null, 10, "ACTIVE")
        ));

        AuthenticatedUserSnapshot snapshot = loginAuthenticationService.authenticate("2024305999", "secret");

        assertThat(snapshot.userId()).isEqualTo(1001L);
        assertThat(snapshot.userNo()).isEqualTo("2024305999");
        assertThat(snapshot.userName()).isEqualTo("Test User");
        assertThat(snapshot.identity()).isEqualTo("student");
        assertThat(snapshot.roles()).containsExactlyInAnyOrder("student", "class-monitor");
        assertThat(snapshot.authorities()).containsExactlyInAnyOrder("application.view.self", "application.view.assigned");
        assertThat(snapshot.scopeRules()).hasSize(1);
    }

    @Test
    void shouldRejectWhenPasswordDoesNotMatch() {
        IamUserCredential credential = new IamUserCredential(
                1001L,
                "2024305999",
                "Test User",
                "hash",
                "ACTIVE"
        );
        given(iamUserQueryRepository.findCredentialByUserNo("2024305999")).willReturn(Optional.of(credential));
        given(passwordHashVerifier.matches("bad-secret", "hash")).willReturn(false);

        assertThatThrownBy(() -> loginAuthenticationService.authenticate("2024305999", "bad-secret"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("登录账号或密码错误");
    }

    @Test
    void shouldRejectWhenUserIsInactive() {
        IamUserCredential credential = new IamUserCredential(
                1001L,
                "2024305999",
                "Test User",
                "hash",
                "DISABLED"
        );
        given(iamUserQueryRepository.findCredentialByUserNo("2024305999")).willReturn(Optional.of(credential));

        assertThatThrownBy(() -> loginAuthenticationService.authenticate("2024305999", "secret"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("登录账号或密码错误");
    }

    @Test
    void shouldRejectWhenUserDoesNotExist() {
        given(iamUserQueryRepository.findCredentialByUserNo("2024305999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> loginAuthenticationService.authenticate("2024305999", "secret"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("登录账号或密码错误");
    }
}
