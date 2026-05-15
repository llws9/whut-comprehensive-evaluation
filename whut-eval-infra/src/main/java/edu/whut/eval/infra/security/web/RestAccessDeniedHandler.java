package edu.whut.eval.infra.security.web;

import edu.whut.eval.common.error.CommonErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        SecurityErrorResponseWriter.write(
                response,
                CommonErrorCode.ACCESS_DENIED,
                CommonErrorCode.ACCESS_DENIED.defaultMessage()
        );
    }
}
