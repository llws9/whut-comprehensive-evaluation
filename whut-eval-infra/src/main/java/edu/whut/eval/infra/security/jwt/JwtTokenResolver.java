package edu.whut.eval.infra.security.jwt;

import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.infra.security.config.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component
public class JwtTokenResolver {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenResolver.class);

    private final SecurityProperties securityProperties;

    public JwtTokenResolver(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public Optional<ResolvedToken> resolve(HttpServletRequest request) {
        String headerName = securityProperties.getTokenHeader();
        String headerValue = request.getHeader(headerName);
        if (StringUtils.hasText(headerValue)) {
            return Optional.of(resolveFromHeader(headerValue, request));
        }

        if (securityProperties.isAllowCookieToken()) {
            Optional<ResolvedToken> cookieToken = resolveFromCookie(request);
            if (cookieToken.isPresent()) {
                return cookieToken;
            }
        }

        if (securityProperties.isAllowQueryToken()) {
            Optional<ResolvedToken> queryToken = resolveFromQuery(request);
            if (queryToken.isPresent()) {
                return queryToken;
            }
        }

        AppLog.debug(log, "security.jwt.token.not-found",
                "path", request.getRequestURI(),
                "method", request.getMethod(),
                "header", headerName);
        return Optional.empty();
    }

    private ResolvedToken resolveFromHeader(String headerValue, HttpServletRequest request) {
        String tokenPrefix = securityProperties.getTokenPrefix();
        if (StringUtils.hasText(tokenPrefix)) {
            if (!headerValue.startsWith(tokenPrefix)) {
                AppLog.warn(log, "security.jwt.token.prefix-mismatch",
                        "path", request.getRequestURI(),
                        "method", request.getMethod(),
                        "header", securityProperties.getTokenHeader(),
                        "expectedPrefix", tokenPrefix,
                        "actualLength", headerValue.length());
                throw new JwtAuthenticationException("JWT token prefix mismatch");
            }
            String stripped = headerValue.substring(tokenPrefix.length()).trim();
            if (!StringUtils.hasText(stripped)) {
                AppLog.warn(log, "security.jwt.token.empty-after-prefix",
                        "path", request.getRequestURI(),
                        "method", request.getMethod(),
                        "header", securityProperties.getTokenHeader());
                throw new JwtAuthenticationException("JWT token is empty after prefix stripping");
            }
            AppLog.info(log, "security.jwt.token.resolved",
                    "path", request.getRequestURI(),
                    "method", request.getMethod(),
                    "source", "header",
                    "tokenLength", stripped.length());
            return new ResolvedToken(stripped, "header");
        }

        AppLog.info(log, "security.jwt.token.resolved",
                "path", request.getRequestURI(),
                "method", request.getMethod(),
                "source", "header",
                "tokenLength", headerValue.length());
        return new ResolvedToken(headerValue.trim(), "header");
    }

    private Optional<ResolvedToken> resolveFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            AppLog.debug(log, "security.jwt.token.cookie-missing",
                    "path", request.getRequestURI(),
                    "method", request.getMethod(),
                    "cookieName", securityProperties.getTokenCookieName());
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (securityProperties.getTokenCookieName().equals(cookie.getName())
                    && StringUtils.hasText(cookie.getValue())) {
                AppLog.info(log, "security.jwt.token.resolved",
                        "path", request.getRequestURI(),
                        "method", request.getMethod(),
                        "source", "cookie",
                        "tokenLength", cookie.getValue().length());
                return Optional.of(new ResolvedToken(cookie.getValue().trim(), "cookie"));
            }
        }
        AppLog.debug(log, "security.jwt.token.cookie-missing",
                "path", request.getRequestURI(),
                "method", request.getMethod(),
                "cookieName", securityProperties.getTokenCookieName());
        return Optional.empty();
    }

    private Optional<ResolvedToken> resolveFromQuery(HttpServletRequest request) {
        String parameterValue = request.getParameter(securityProperties.getTokenQueryParameter());
        if (!StringUtils.hasText(parameterValue)) {
            AppLog.debug(log, "security.jwt.token.query-missing",
                    "path", request.getRequestURI(),
                    "method", request.getMethod(),
                    "parameter", securityProperties.getTokenQueryParameter());
            return Optional.empty();
        }
        AppLog.info(log, "security.jwt.token.resolved",
                "path", request.getRequestURI(),
                "method", request.getMethod(),
                "source", "query",
                "tokenLength", parameterValue.length());
        return Optional.of(new ResolvedToken(parameterValue.trim(), "query"));
    }
}
