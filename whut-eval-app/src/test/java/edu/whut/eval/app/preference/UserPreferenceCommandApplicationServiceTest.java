package edu.whut.eval.app.preference;

import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.preference.command.CreateUserPreferenceCommand;
import edu.whut.eval.application.preference.query.UserPreferenceView;
import edu.whut.eval.application.preference.service.UserPreferenceCacheGateway;
import edu.whut.eval.application.preference.service.UserPreferenceCommandApplicationService;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.preference.model.UserPreference;
import edu.whut.eval.domain.preference.repository.UserPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UserPreferenceCommandApplicationServiceTest {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final UserPreferenceRepository userPreferenceRepository = mock(UserPreferenceRepository.class);
    private final UserPreferenceCacheGateway userPreferenceCacheGateway = mock(UserPreferenceCacheGateway.class);

    private final UserPreferenceCommandApplicationService applicationService =
            new UserPreferenceCommandApplicationService(
                    userAuthorizationContextAssembler,
                    userPreferenceRepository,
                    userPreferenceCacheGateway
            );

    @Test
    void shouldCreatePreferenceForCurrentUserAndEvictCache() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(userPreferenceRepository.existsByUserId(1001L)).willReturn(false);
        given(userPreferenceRepository.save(any(UserPreference.class)))
                .willReturn(new UserPreference(1L, 1001L, "dark", true));

        UserPreferenceView result = applicationService.createCurrentUserPreference(
                new CreateUserPreferenceCommand("dark", true)
        );

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(1001L);
        assertThat(result.getPreferredTheme()).isEqualTo("dark");
        assertThat(result.getNotificationsEnabled()).isTrue();
        verify(userPreferenceCacheGateway).evictByUserId(1001L);
    }

    @Test
    void shouldRejectWhenPreferenceAlreadyExists() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(userPreferenceRepository.existsByUserId(1001L)).willReturn(true);

        assertThatThrownBy(() -> applicationService.createCurrentUserPreference(
                new CreateUserPreferenceCommand("dark", true)
        )).isInstanceOf(ConflictException.class)
                .hasMessage("当前用户已存在偏好设置，请改用更新接口");

        verify(userPreferenceRepository, never()).save(any(UserPreference.class));
        verify(userPreferenceCacheGateway, never()).evictByUserId(1001L);
    }

    @Test
    void shouldDeclareTransactionalBoundaryOnCreateMethod() throws NoSuchMethodException {
        Method method = UserPreferenceCommandApplicationService.class.getMethod(
                "createCurrentUserPreference",
                CreateUserPreferenceCommand.class
        );

        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }

    private UserAuthorizationContext currentUser() {
        return new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of("application.view.self", "score.view.self"),
                List.of()
        );
    }
}
