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
    
    //정보가 다 있으면 프론트가 메인화면으로 매핑 시키게 없으면 정보 입력 페이지로
    private boolean isProfileComplete;
}
