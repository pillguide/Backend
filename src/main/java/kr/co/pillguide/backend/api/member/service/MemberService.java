package kr.co.pillguide.backend.api.member.service;

import kr.co.pillguide.backend.api.member.dto.MemberAdditionalRequestDTO;
import kr.co.pillguide.backend.api.member.entity.Gender;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.entity.Role;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import kr.co.pillguide.backend.common.response.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional
    public void updateAdditionalInfo(Long memberId, MemberAdditionalRequestDTO requestDTO) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException(ErrorStatus.NOT_FOUND_USER.getMessage())
                );

        // 이메일 없는 사용자 처리 (카카오)
        if (member.getEmail() == null && requestDTO.email() == null) {
            throw new IllegalArgumentException("이메일 입력이 필요합니다.");
        }

        if (member.getEmail() == null) {
            member.updateEmail(requestDTO.email());
        }

        member.updateAdditionalInfo(requestDTO.gender(), requestDTO.birthDate());

    }
}