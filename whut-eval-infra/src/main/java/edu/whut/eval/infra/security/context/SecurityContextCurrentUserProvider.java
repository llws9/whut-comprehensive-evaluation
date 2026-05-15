package edu.whut.eval.infra.security.context;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Optional<CurrentUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CurrentUser) {
            return Optional.of((CurrentUser) principal);
        }
        return Optional.empty();
    }
}
