package edu.whut.eval.infra.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "infra.security")
public class SecurityProperties {

    private List<String> permitAllPatterns = defaultPermitAllPatterns();
    private String tokenHeader = "Authorization";
    private String tokenPrefix = "Bearer ";
    private boolean allowCookieToken;
    private boolean allowQueryToken;
    private String tokenQueryParameter = "access_token";
    private String tokenCookieName = "ACCESS_TOKEN";
    private JwtProperties jwt = new JwtProperties();

    public List<String> getPermitAllPatterns() {
        return permitAllPatterns;
    }

    public void setPermitAllPatterns(List<String> permitAllPatterns) {
        this.permitAllPatterns = permitAllPatterns == null
                ? defaultPermitAllPatterns()
                : new ArrayList<>(permitAllPatterns);
    }

    public String getTokenHeader() {
        return tokenHeader;
    }

    public void setTokenHeader(String tokenHeader) {
        this.tokenHeader = tokenHeader;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
    }

    public boolean isAllowCookieToken() {
        return allowCookieToken;
    }

    public void setAllowCookieToken(boolean allowCookieToken) {
        this.allowCookieToken = allowCookieToken;
    }

    public boolean isAllowQueryToken() {
        return allowQueryToken;
    }

    public void setAllowQueryToken(boolean allowQueryToken) {
        this.allowQueryToken = allowQueryToken;
    }

    public String getTokenQueryParameter() {
        return tokenQueryParameter;
    }

    public void setTokenQueryParameter(String tokenQueryParameter) {
        this.tokenQueryParameter = tokenQueryParameter;
    }

    public String getTokenCookieName() {
        return tokenCookieName;
    }

    public void setTokenCookieName(String tokenCookieName) {
        this.tokenCookieName = tokenCookieName;
    }

    public JwtProperties getJwt() {
        return jwt;
    }

    public void setJwt(JwtProperties jwt) {
        this.jwt = jwt == null ? new JwtProperties() : jwt;
    }

    private static List<String> defaultPermitAllPatterns() {
        List<String> defaults = new ArrayList<>();
        defaults.add("/error");
        defaults.add("/actuator/health");
        defaults.add("/actuator/info");
        defaults.add("/api/auth/login");
        defaults.add("/api/auth/refresh");
        defaults.add("/swagger-ui/**");
        defaults.add("/v3/api-docs/**");
        return defaults;
    }
}
