package edu.whut.eval.infra.security.context;

import edu.whut.eval.common.exception.AuthenticationFailedException;

import java.util.Optional;

public interface CurrentUserProvider {

    Optional<CurrentUser> currentUser();

    default CurrentUser requiredCurrentUser() {
        return currentUser().orElseThrow(AuthenticationFailedException::new);
    }
}
