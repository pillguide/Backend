package kr.co.pillguide.backend.api.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import kr.co.pillguide.backend.api.member.dto.TokenResponseDTO;
import kr.co.pillguide.backend.api.member.service.LoginExchangeService;
import kr.co.pillguide.backend.common.response.ApiResponse;
import kr.co.pillguide.backend.common.response.SuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class LoginExchangeController {
    private final LoginExchangeService loginExchangeService;

    public record ExchangeRequest(@NotBlank String code, @NotBlank String verifier) { }

    @Operation(summary = "OAuth 일회용 코드 교환")
    @SecurityRequirements
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 발급"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "교환 코드 또는 verifier 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/exchange")
    public ResponseEntity<ApiResponse<TokenResponseDTO>> exchange(@Valid @RequestBody ExchangeRequest request) {
        ResponseEntity<ApiResponse<TokenResponseDTO>> result = ApiResponse.success(SuccessStatus.OAUTH2_LOGIN_SUCCESS,
                loginExchangeService.exchange(request.code(), request.verifier()));
        return ResponseEntity.status(result.getStatusCode())
                .cacheControl(CacheControl.noStore())
                .body(result.getBody());
    }
}
