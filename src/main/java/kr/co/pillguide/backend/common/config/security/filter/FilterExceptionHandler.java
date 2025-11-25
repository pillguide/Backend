package kr.co.pillguide.backend.common.config.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.pillguide.backend.common.response.ApiResponse;
import kr.co.pillguide.backend.common.response.ErrorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class FilterExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /// 인증 실패 시 호출 (ex. 토큰이 유효하지 않거나 없는 경우)
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {

        log.warn("AuthenticationEntryPoint Called: {}", authException.getMessage());

        setErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorStatus.UNAUTHORIZED_USER.getMessage());
    }

    /// 인가 실패 시 호출 (ex. 권한 없음)
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {

        log.warn("AccessDeniedException Called: {}", accessDeniedException.getMessage());

        setErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ErrorStatus.FORBIDDEN_RESOURCE_ACCESS.getMessage());
    }


    private void setErrorResponse(HttpServletResponse resp, int statusCode, String message) throws IOException {

        resp.setStatus(statusCode);
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Void> apiResponse = ApiResponse.fail(statusCode, message);
        String json = objectMapper.writeValueAsString(apiResponse);

        resp.getWriter().write(json);
    }
}
