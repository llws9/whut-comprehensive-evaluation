package edu.whut.eval.infra.security.web;

import edu.whut.eval.common.error.CommonErrorCode;
import edu.whut.eval.infra.security.jwt.JwtAuthenticationException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        CommonErrorCode errorCode = authException instanceof JwtAuthenticationException
                ? CommonErrorCode.TOKEN_INVALID
                : CommonErrorCode.AUTHENTICATION_FAILED;

        SecurityErrorResponseWriter.write(
                response,
                errorCode,
                errorCode.defaultMessage()
        );
    }
}
