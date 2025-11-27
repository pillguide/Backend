package kr.co.pillguide.backend.api.member.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponseDTO {

    private String name;
    private String email;

    private String accessToken;
    private String refreshToken;

    /**
     * 사용자 추가 정보(성별, 생년월일) 입력 완료 여부.
     * true일 경우 메인 화면으로, false일 경우 추가 정보 입력 화면으로 이동합니다.
     */
    private boolean isProfileComplete;
}
