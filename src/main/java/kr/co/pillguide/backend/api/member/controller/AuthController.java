package kr.co.pillguide.backend.api.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import kr.co.pillguide.backend.api.member.dto.TokenResponseDTO;
import kr.co.pillguide.backend.api.member.service.AuthService;
import kr.co.pillguide.backend.common.response.ApiResponse;
import kr.co.pillguide.backend.common.response.SuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
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
            summary = "리프레시 토큰 재발급 API",
            description = "X-Refresh-Token을 검증하고 한 번 회전합니다. 이전 refresh 재사용은 401입니다. access 인증은 필요하지 않습니다.",
            security = @SecurityRequirement(name = "refresh")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "새 access/refresh 발급"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "refresh 누락·만료·용도 불일치·폐기"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "내부 오류")})
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<TokenResponseDTO>> reissue(
            @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken
    ) {
        TokenResponseDTO response = authService.reissue(refreshToken);
        var result = ApiResponse.success(
                SuccessStatus.TOKEN_REISSUE_SUCCESS,
                response
        );
        return ResponseEntity.status(result.getStatusCode()).cacheControl(CacheControl.noStore()).body(result.getBody());
    }

    @Operation(summary = "로그아웃", description = "현재 refresh로 인증합니다. 성공하면 이전 access 인증 버전과 refresh를 폐기하고 FCM 연결을 해제합니다.",
            security = @SecurityRequirement(name = "refresh"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 완료, 본문 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효한 현재 refresh 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "내부 오류")})
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken
    ) {
        authService.logout(refreshToken);
        return ResponseEntity.ok().build();
    }
}

