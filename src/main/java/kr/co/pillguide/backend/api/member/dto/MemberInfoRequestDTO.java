package kr.co.pillguide.backend.api.member.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class MemberInfoRequestDTO {
    private String gender;
    private LocalDate birthDate;
}
