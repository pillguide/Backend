package kr.co.pillguide.backend.api.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FcmTokenRequest(
        @NotBlank(message = "FCM 토큰은 필수입니다.")
        @Size(max = 2048, message = "FCM 토큰이 너무 깁니다.")
        @Pattern(regexp = "\\S+", message = "FCM 토큰에는 공백을 포함할 수 없습니다.")
        String token
) {
}