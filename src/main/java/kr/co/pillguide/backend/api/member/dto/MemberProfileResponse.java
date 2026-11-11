package kr.co.pillguide.backend.api.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.co.pillguide.backend.api.member.entity.Gender;
import kr.co.pillguide.backend.api.member.entity.Member;

import java.time.LocalDate;

@Schema(description = "현재 회원 프로필")
public record MemberProfileResponse(
        String name,
        @Schema(nullable = true) Gender gender,
        @Schema(nullable = true) LocalDate birthDate,
        @Schema(nullable = true) String email
) {
    public static MemberProfileResponse from(Member member) {
        return new MemberProfileResponse(member.getName(), member.getGender(),
                member.getBirthDate(), member.getEmail());
    }
}
