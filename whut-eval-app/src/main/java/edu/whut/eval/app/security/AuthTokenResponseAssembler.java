package edu.whut.eval.app.security;

import edu.whut.eval.app.security.dto.AuthTokenResponse;
import edu.whut.eval.infra.security.config.SecurityProperties;
import edu.whut.eval.infra.security.jwt.JwtTokenPair;
import org.springframework.stereotype.Component;

@Component
public class AuthTokenResponseAssembler {

    private final SecurityProperties securityProperties;

    public AuthTokenResponseAssembler(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public AuthTokenResponse toResponse(JwtTokenPair tokenPair) {
        return new AuthTokenResponse(
                tokenPair.getAccessToken(),
                securityProperties.getJwt().getAccessTokenType(),
                tokenPair.getAccessTokenExpiresAt(),
                tokenPair.getRefreshToken(),
                securityProperties.getJwt().getRefreshTokenType(),
                tokenPair.getRefreshTokenExpiresAt()
        );
    }
}
