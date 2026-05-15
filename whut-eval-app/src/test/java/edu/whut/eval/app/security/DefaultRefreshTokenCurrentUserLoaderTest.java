package edu.whut.eval.app.security;

import edu.whut.eval.application.auth.model.AuthenticatedUserSnapshot;
import edu.whut.eval.application.auth.model.RefreshTokenReloadContext;
import edu.whut.eval.application.auth.service.DefaultRefreshTokenCurrentUserLoader;
import edu.whut.eval.common.exception.AuthenticationFailedException;
import edu.whut.eval.domain.iam.model.IamRoleAssignment;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.domain.iam.model.IamUser;
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
class DefaultRefreshTokenCurrentUserLoaderTest {

    @Mock
    private IamUserQueryRepository iamUserQueryRepository;

    @Mock
    private RoleAssignmentQueryRepository roleAssignmentQueryRepository;

    @Mock
    private UserAuthorityQueryRepository userAuthorityQueryRepository;

    @Mock
    private UserScopeRuleQueryRepository userScopeRuleQueryRepository;

    @InjectMocks
    private DefaultRefreshTokenCurrentUserLoader loader;

    @Test
    void shouldLoadCurrentUserSnapshotFromRefreshSubject() {
        given(iamUserQueryRepository.findById(1001L)).willReturn(Optional.of(
                new IamUser(1001L, "2024305999", "Test User", null, null, "ACTIVE")
        ));
        given(roleAssignmentQueryRepository.findActiveAssignmentsByUserId(1001L)).willReturn(List.of(
                new IamRoleAssignment(1L, 11L, "student", "Student", 101L, "ACTIVE"),
                new IamRoleAssignment(2L, 12L, "class-monitor", "Class Monitor", 101L, "ACTIVE")
        ));
        given(userAuthorityQueryRepository.findActivePermissionCodesByUserId(1001L)).willReturn(Set.of(
                "application.view.self",
                "score.view.self"
        ));
        given(userScopeRuleQueryRepository.findActiveScopeRulesByUserId(1001L)).willReturn(List.of(
                new IamScopeRule(1L, "application.view.self", "SELF", null, null, null, null, 10, "ACTIVE"),
                new IamScopeRule(2L, "score.view.self", "SELF", null, null, null, null, 20, "ACTIVE")
        ));

        AuthenticatedUserSnapshot snapshot = loader.load(new RefreshTokenReloadContext(
                1001L,
                "2024305999",
                "student"
        ));

        assertThat(snapshot.userId()).isEqualTo(1001L);
        assertThat(snapshot.userNo()).isEqualTo("2024305999");
        assertThat(snapshot.userName()).isEqualTo("Test User");
        assertThat(snapshot.identity()).isEqualTo("student");
        assertThat(snapshot.roles()).containsExactlyInAnyOrder("student", "class-monitor");
        assertThat(snapshot.authorities()).containsExactlyInAnyOrder("application.view.self", "score.view.self");
        assertThat(snapshot.scopeRules()).hasSize(2);
        assertThat(snapshot.scopeRules().getFirst().permissionCode()).isEqualTo("application.view.self");
        assertThat(snapshot.scopeRules().getFirst().scopeType()).isEqualTo("SELF");
    }

    @Test
    void shouldRejectWhenRefreshSubjectUserNoDoesNotMatchDatabase() {
        given(iamUserQueryRepository.findById(1001L)).willReturn(Optional.of(
                new IamUser(1001L, "2024306000", "Test User", null, null, "ACTIVE")
        ));

        assertThatThrownBy(() -> loader.load(new RefreshTokenReloadContext(1001L, "2024305999", "student")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("refresh token 用户标识不一致");
    }
}
