package edu.whut.eval.infra.security.jwt;

import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.model.UserAuthorizationContextLoadRequest;
import edu.whut.eval.application.auth.service.UserAuthorizationContextLoader;
import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.infra.security.context.CurrentUser;
import edu.whut.eval.infra.security.web.RestAuthenticationEntryPoint;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenResolver jwtTokenResolver;
    private final JwtClaimsParser jwtClaimsParser;
    private final JwtClaimsToCurrentUserMapper jwtClaimsToCurrentUserMapper;
    private final UserAuthorizationContextLoader userAuthorizationContextLoader;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(JwtTokenResolver jwtTokenResolver,
                                   JwtClaimsParser jwtClaimsParser,
                                   JwtClaimsToCurrentUserMapper jwtClaimsToCurrentUserMapper,
                                   UserAuthorizationContextLoader userAuthorizationContextLoader,
                                   RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtTokenResolver = jwtTokenResolver;
        this.jwtClaimsParser = jwtClaimsParser;
        this.jwtClaimsToCurrentUserMapper = jwtClaimsToCurrentUserMapper;
        this.userAuthorizationContextLoader = userAuthorizationContextLoader;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AppLog.debug(log, "security.jwt.filter.started",
                "path", request.getRequestURI(),
                "method", request.getMethod());

        try {
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                AppLog.debug(log, "security.jwt.filter.skipped-existing-authentication",
                        "path", request.getRequestURI(),
                        "method", request.getMethod());
                filterChain.doFilter(request, response);
                return;
            }

            ResolvedToken resolvedToken = jwtTokenResolver.resolve(request).orElse(null);
            if (resolvedToken == null) {
                filterChain.doFilter(request, response);
                return;
            }

            Claims claims = jwtClaimsParser.parse(resolvedToken.getToken(), resolvedToken.getSource());
            CurrentUser tokenUser = jwtClaimsToCurrentUserMapper.map(claims);
            UserAuthorizationContext authorizationContext = userAuthorizationContextLoader.load(
                    new UserAuthorizationContextLoadRequest(
                            tokenUser.getUserId(),
                            tokenUser.getUserNo(),
                            tokenUser.getUserName(),
                            tokenUser.getIdentity(),
                            tokenUser.getRoles()
                    )
            );
            CurrentUser currentUser = toCurrentUser(authorizationContext);
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(
                            currentUser,
                            resolvedToken.getToken(),
                            toGrantedAuthorities(currentUser)
                    );

            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            AppLog.info(log, "security.jwt.filter.authenticated",
                    "path", request.getRequestURI(),
                    "method", request.getMethod(),
                    "source", resolvedToken.getSource(),
                    "userId", currentUser.getUserId(),
                    "userNo", currentUser.getUserNo(),
                    "authorityCount", currentUser.getAuthorities().size(),
                    "scopeRuleCount", currentUser.getScopeRules().size());

            filterChain.doFilter(request, response);
        } catch (JwtAuthenticationException exception) {
            SecurityContextHolder.clearContext();
            AppLog.warn(log, "security.jwt.filter.authentication-failed",
                    "path", request.getRequestURI(),
                    "method", request.getMethod(),
                    "reason", exception.getMessage());
            authenticationEntryPoint.commence(request, response, exception);
        }
    }

    private List<GrantedAuthority> toGrantedAuthorities(CurrentUser currentUser) {
        return currentUser.getAuthorities().stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    private CurrentUser toCurrentUser(UserAuthorizationContext authorizationContext) {
        return new CurrentUser(
                authorizationContext.getUserId(),
                authorizationContext.getUserNo(),
                authorizationContext.getUserName(),
                authorizationContext.getIdentity(),
                authorizationContext.getRoles(),
                authorizationContext.getAuthorities(),
                authorizationContext.getScopeRules()
        );
    }
}
