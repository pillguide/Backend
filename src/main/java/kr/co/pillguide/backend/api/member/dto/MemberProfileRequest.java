package kr.co.pillguide.backend.api.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import kr.co.pillguide.backend.api.member.entity.Gender;

import java.time.LocalDate;

@Schema(description = "현재 회원 프로필 수정. 생략하거나 null인 필드는 유지합니다.")
public record MemberProfileRequest(
        @Schema(description = "이름, 1~50자") @Size(max = 50) String name,
        @Schema(description = "성별") Gender gender,
        @Schema(description = "생년월일, YYYY-MM-DD, 미래 날짜 불가") @PastOrPresent LocalDate birthDate,
        @Schema(description = "이메일, 최대 100자") @Email @Size(max = 100) String email
) { }
