package kr.co.pillguide.backend.api.member.dto;

import kr.co.pillguide.backend.api.member.entity.Gender;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class MemberInfoRequestDTO {
    private Gender gender;
    private LocalDate birthDate;
}
