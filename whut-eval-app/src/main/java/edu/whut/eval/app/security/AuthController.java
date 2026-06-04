package edu.whut.eval.app.security;

import edu.whut.eval.application.auth.model.AuthenticatedUserSnapshot;
import edu.whut.eval.application.auth.model.RefreshSessionContinueCommand;
import edu.whut.eval.application.auth.model.RefreshSessionValidationCommand;
import edu.whut.eval.application.auth.model.RefreshTokenReloadContext;
import edu.whut.eval.application.auth.model.LoginSessionCreateCommand;
import edu.whut.eval.application.auth.service.LoginAuthenticationService;
import edu.whut.eval.application.auth.service.LoginSessionService;
import edu.whut.eval.application.auth.service.LogoutService;
import edu.whut.eval.application.auth.service.RefreshSessionService;
import edu.whut.eval.application.auth.service.RefreshTokenCurrentUserLoader;
import edu.whut.eval.app.security.dto.AuthTokenResponse;
import edu.whut.eval.app.security.dto.LoginRequest;
import edu.whut.eval.app.security.dto.RefreshTokenRequest;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.common.error.CommonErrorCode;
import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.infra.security.context.CurrentUser;
import edu.whut.eval.infra.security.jwt.JwtAuthenticationException;
import edu.whut.eval.infra.security.jwt.JwtClaimsParser;
import edu.whut.eval.infra.security.jwt.JwtTokenIssuer;
import edu.whut.eval.infra.security.jwt.JwtTokenPair;
import edu.whut.eval.infra.security.jwt.RefreshTokenClaimsMapper;
import edu.whut.eval.infra.security.jwt.RefreshTokenSubject;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final LoginAuthenticationService loginAuthenticationService;
    private final JwtClaimsParser jwtClaimsParser;
    private final RefreshTokenClaimsMapper refreshTokenClaimsMapper;
    private final RefreshTokenCurrentUserLoader refreshTokenCurrentUserLoader;
    private final JwtTokenIssuer jwtTokenIssuer;
    private final AuthTokenResponseAssembler authTokenResponseAssembler;
    private final LogoutService logoutService;
    private final LoginSessionService loginSessionService;
    private final RefreshSessionService refreshSessionService;

    public AuthController(LoginAuthenticationService loginAuthenticationService,
                          JwtClaimsParser jwtClaimsParser,
                          RefreshTokenClaimsMapper refreshTokenClaimsMapper,
                          RefreshTokenCurrentUserLoader refreshTokenCurrentUserLoader,
                          JwtTokenIssuer jwtTokenIssuer,
                          AuthTokenResponseAssembler authTokenResponseAssembler,
                          LogoutService logoutService,
                          LoginSessionService loginSessionService,
                          RefreshSessionService refreshSessionService) {
        this.loginAuthenticationService = loginAuthenticationService;
        this.jwtClaimsParser = jwtClaimsParser;
        this.refreshTokenClaimsMapper = refreshTokenClaimsMapper;
        this.refreshTokenCurrentUserLoader = refreshTokenCurrentUserLoader;
        this.jwtTokenIssuer = jwtTokenIssuer;
        this.authTokenResponseAssembler = authTokenResponseAssembler;
        this.logoutService = logoutService;
        this.loginSessionService = loginSessionService;
        this.refreshSessionService = refreshSessionService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody LoginRequest request,
                                                 HttpServletRequest servletRequest) {
        AppLog.info(log, "security.auth.login.request.received",
                "credential", request.getCredential(),
                "passwordPresent", request.getPassword() != null && !request.getPassword().isBlank());
        AuthenticatedUserSnapshot snapshot = loginAuthenticationService.authenticate(
                request.getCredential(),
                request.getPassword()
        );
        CurrentUser currentUser = new CurrentUser(
                snapshot.userId(),
                snapshot.userNo(),
                snapshot.userName(),
                snapshot.identity(),
                snapshot.roles(),
                snapshot.authorities(),
                snapshot.scopeRules()
        );
        JwtTokenPair tokenPair = jwtTokenIssuer.issueTokenPair(currentUser);
        loginSessionService.createLoginSession(new LoginSessionCreateCommand(
                currentUser.getUserId(),
                tokenPair.getSessionNo(),
                tokenPair.getAccessTokenId(),
                tokenPair.getRefreshTokenId(),
                tokenPair.getRefreshTokenExpiresAt(),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent")
        ));
        AuthTokenResponse response = authTokenResponseAssembler.toResponse(tokenPair);
        AppLog.info(log, "security.auth.login.completed",
                "userId", currentUser.getUserId(),
                "userNo", currentUser.getUserNo(),
                "identity", currentUser.getIdentity(),
                "roleCount", currentUser.getRoles().size(),
                "authorityCount", currentUser.getAuthorities().size(),
                "scopeRuleCount", currentUser.getScopeRules().size());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<?>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        int tokenLength = request.getRefreshToken() == null ? 0 : request.getRefreshToken().length();
        AppLog.info(log, "security.auth.refresh.request.received",
                "tokenLength", tokenLength);
        try {
            Claims claims = jwtClaimsParser.parse(request.getRefreshToken(), "refresh-endpoint");
            RefreshTokenSubject subject = refreshTokenClaimsMapper.map(claims);
            refreshSessionService.validateRefreshSession(new RefreshSessionValidationCommand(
                    subject.getUserId(),
                    subject.getSessionNo(),
                    subject.getRefreshTokenId()
            ));
            AppLog.info(log, "security.auth.refresh.token.validated",
                    "userId", subject.getUserId(),
                    "userNo", subject.getUserNo(),
                    "identity", subject.getIdentity(),
                    "sessionNo", subject.getSessionNo());
            AuthenticatedUserSnapshot snapshot = refreshTokenCurrentUserLoader.load(
                    new RefreshTokenReloadContext(subject.getUserId(), subject.getUserNo(), subject.getIdentity())
            );
            CurrentUser currentUser = new CurrentUser(
                    snapshot.userId(),
                    snapshot.userNo(),
                    snapshot.userName(),
                    snapshot.identity(),
                    snapshot.roles(),
                    snapshot.authorities(),
                    snapshot.scopeRules()
            );
            JwtTokenPair tokenPair = jwtTokenIssuer.issueTokenPair(currentUser, subject.getSessionNo());
            refreshSessionService.continueRefreshSession(new RefreshSessionContinueCommand(
                    subject.getSessionNo(),
                    subject.getRefreshTokenId(),
                    tokenPair.getAccessTokenId(),
                    tokenPair.getRefreshTokenId(),
                    tokenPair.getRefreshTokenExpiresAt()
            ));
            AuthTokenResponse response = authTokenResponseAssembler.toResponse(tokenPair);
            AppLog.info(log, "security.auth.refresh.completed",
                    "userId", currentUser.getUserId(),
                    "userNo", currentUser.getUserNo(),
                    "identity", currentUser.getIdentity(),
                    "roleCount", currentUser.getRoles().size(),
                    "authorityCount", currentUser.getAuthorities().size(),
                    "scopeRuleCount", currentUser.getScopeRules().size());
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (JwtAuthenticationException exception) {
            AppLog.warn(log, "security.auth.refresh.token.invalid",
                    "tokenLength", tokenLength,
                    "reason", exception.getMessage());
            return ResponseEntity.status(CommonErrorCode.AUTHENTICATION_FAILED.httpStatus())
                    .body(ApiResponse.failure(CommonErrorCode.AUTHENTICATION_FAILED,
                            "refresh token 校验失败: " + exception.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(Authentication authentication) {
        if (authentication == null) {
            AppLog.warn(log, "security.auth.logout.authentication.missing");
            return ResponseEntity.status(CommonErrorCode.TOKEN_INVALID.httpStatus())
                    .body(ApiResponse.failure(CommonErrorCode.TOKEN_INVALID, "未提供有效的访问令牌"));
        }

        Object credentials = authentication.getCredentials();

        if (!(credentials instanceof String tokenString)) {
            AppLog.warn(log, "security.auth.logout.token.missing");
            return ResponseEntity.status(CommonErrorCode.TOKEN_INVALID.httpStatus())
                    .body(ApiResponse.failure(CommonErrorCode.TOKEN_INVALID, "未找到有效的访问令牌"));
        }

        try {
            Claims claims = jwtClaimsParser.parse(tokenString, "logout-endpoint");
            String accessTokenId = claims.getId();

            if (accessTokenId == null || accessTokenId.isBlank()) {
                AppLog.warn(log, "security.auth.logout.jti.missing",
                        "tokenLength", tokenString.length());
                return ResponseEntity.status(CommonErrorCode.TOKEN_INVALID.httpStatus())
                        .body(ApiResponse.failure(CommonErrorCode.TOKEN_INVALID, "访问令牌缺少唯一标识"));
            }

            AppLog.info(log, "security.auth.logout.request.received",
                    "accessTokenId", accessTokenId);

            boolean revoked = logoutService.logoutByAccessTokenId(accessTokenId);

            AppLog.info(log, "security.auth.logout.completed",
                    "accessTokenId", accessTokenId,
                    "revoked", revoked);

            if (!revoked) {
                return ResponseEntity.status(CommonErrorCode.TOKEN_INVALID.httpStatus())
                        .body(ApiResponse.failure(CommonErrorCode.TOKEN_INVALID, "访问令牌会话不存在或已失效"));
            }

            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (JwtAuthenticationException exception) {
            AppLog.warn(log, "security.auth.logout.token.invalid",
                    "reason", exception.getMessage());
            return ResponseEntity.status(CommonErrorCode.TOKEN_INVALID.httpStatus())
                    .body(ApiResponse.failure(CommonErrorCode.TOKEN_INVALID, "访问令牌校验失败: " + exception.getMessage()));
        }
    }
}
