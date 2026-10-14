package kr.co.pillguide.backend.api.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import kr.co.pillguide.backend.api.member.dto.TokenResponseDTO;
import kr.co.pillguide.backend.api.member.service.AuthService;
import kr.co.pillguide.backend.common.response.ApiResponse;
import kr.co.pillguide.backend.common.response.SuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "리프레시 토큰 재발급 API"
    )
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<TokenResponseDTO>> reissue(
            @RequestHeader("X-Refresh-Token") String refreshToken
    ) {
        TokenResponseDTO response = authService.reissue(refreshToken);
        return ApiResponse.success(
                SuccessStatus.TOKEN_REISSUE_SUCCESS,
                response
        );
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("X-Refresh-Token") String refreshToken
    ) {
        authService.logout(refreshToken);
        return ResponseEntity.ok().build();
    }
}

