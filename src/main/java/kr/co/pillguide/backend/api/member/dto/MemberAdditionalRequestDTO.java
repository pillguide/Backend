package kr.co.pillguide.backend.api.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import kr.co.pillguide.backend.api.member.entity.Gender;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Schema(description = "소셜 로그인 후 추가 정보 입력 요청 DTO")
public record MemberAdditionalRequestDTO(

        @NotNull(message = "성별은 필수입니다.")
        Gender gender,
        @NotNull(message = "생년월일은 필수입니다.")
        @DateTimeFormat
        LocalDate birthDate
) {
}
