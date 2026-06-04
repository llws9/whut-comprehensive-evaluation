package edu.whut.eval.infra.security.config;

public class JwtProperties {

    private boolean enabled = true;
    private String algorithm = "HS256";
    private String issuer = "whut-eval";
    private String audience = "whut-eval-api";
    private long accessTokenTtlSeconds = 7200;
    private long refreshTokenTtlSeconds = 604800;
    private long clockSkewSeconds = 60;
    private String secret = "";
    private String publicKey = "";
    private String privateKey = "";
    private String userIdClaim = "uid";
    private String userNoClaim = "uno";
    private String userNameClaim = "uname";
    private String identityClaim = "identity";
    private String rolesClaim = "roles";
    private String authoritiesClaim = "authorities";
    private String tokenTypeClaim = "token_type";
    private String sessionIdClaim = "sid";
    private String accessTokenType = "access";
    private String refreshTokenType = "refresh";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public long getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(long clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getUserIdClaim() {
        return userIdClaim;
    }

    public void setUserIdClaim(String userIdClaim) {
        this.userIdClaim = userIdClaim;
    }

    public String getUserNoClaim() {
        return userNoClaim;
    }

    public void setUserNoClaim(String userNoClaim) {
        this.userNoClaim = userNoClaim;
    }

    public String getUserNameClaim() {
        return userNameClaim;
    }

    public void setUserNameClaim(String userNameClaim) {
        this.userNameClaim = userNameClaim;
    }

    public String getAuthoritiesClaim() {
        return authoritiesClaim;
    }

    public void setAuthoritiesClaim(String authoritiesClaim) {
        this.authoritiesClaim = authoritiesClaim;
    }

    public String getIdentityClaim() {
        return identityClaim;
    }

    public void setIdentityClaim(String identityClaim) {
        this.identityClaim = identityClaim;
    }

    public String getRolesClaim() {
        return rolesClaim;
    }

    public void setRolesClaim(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    public String getTokenTypeClaim() {
        return tokenTypeClaim;
    }

    public String getSessionIdClaim() {
        return sessionIdClaim;
    }

    public void setSessionIdClaim(String sessionIdClaim) {
        this.sessionIdClaim = sessionIdClaim;
    }

    public void setTokenTypeClaim(String tokenTypeClaim) {
        this.tokenTypeClaim = tokenTypeClaim;
    }

    public String getAccessTokenType() {
        return accessTokenType;
    }

    public void setAccessTokenType(String accessTokenType) {
        this.accessTokenType = accessTokenType;
    }

    public String getRefreshTokenType() {
        return refreshTokenType;
    }

    public void setRefreshTokenType(String refreshTokenType) {
        this.refreshTokenType = refreshTokenType;
    }
}
