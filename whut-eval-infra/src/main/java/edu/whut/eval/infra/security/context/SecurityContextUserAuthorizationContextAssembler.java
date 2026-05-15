package edu.whut.eval.infra.security.context;

import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SecurityContextUserAuthorizationContextAssembler implements UserAuthorizationContextAssembler {

    private final CurrentUserProvider currentUserProvider;

    public SecurityContextUserAuthorizationContextAssembler(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public Optional<UserAuthorizationContext> currentAuthorizationContext() {
        return currentUserProvider.currentUser().map(this::toAuthorizationContext);
    }

    private UserAuthorizationContext toAuthorizationContext(CurrentUser currentUser) {
        return new UserAuthorizationContext(
                currentUser.getUserId(),
                currentUser.getUserNo(),
                currentUser.getUserName(),
                currentUser.getIdentity(),
                currentUser.getRoles(),
                currentUser.getAuthorities(),
                currentUser.getScopeRules()
        );
    }
}
