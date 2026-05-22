package edu.whut.eval.app.security;

import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/security")
public class SecurityProbeController {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;

    public SecurityProbeController(UserAuthorizationContextAssembler userAuthorizationContextAssembler) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> currentUser() {
        UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", authorizationContext.getUserId());
        payload.put("userNo", authorizationContext.getUserNo());
        payload.put("userName", authorizationContext.getUserName());
        payload.put("identity", authorizationContext.getIdentity());
        payload.put("sessionId", authorizationContext.getSessionId());
        payload.put("roles", authorizationContext.getRoles());
        payload.put("authorities", authorizationContext.getAuthorities());
        payload.put("scopeRules", authorizationContext.getScopeRules());
        return ApiResponse.success(payload);
    }
}
