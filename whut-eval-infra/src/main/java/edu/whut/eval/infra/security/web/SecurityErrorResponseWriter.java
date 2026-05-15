package edu.whut.eval.infra.security.web;

import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {
    }

    public static void write(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        ApiResponse<Void> payload = ApiResponse.failure(errorCode, message);
        response.setStatus(errorCode.httpStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write(toJson(payload));
    }

    private static String toJson(ApiResponse<Void> payload) {
        return "{\"success\":false,\"code\":\"" + escape(payload.code()) + "\",\"message\":\""
                + escape(payload.message()) + "\",\"data\":null}";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
