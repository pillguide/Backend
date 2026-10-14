package kr.co.pillguide.backend.api.member.dto;

public record TokenResponseDTO(
        String accessToken,
        String refreshToken
) {}
