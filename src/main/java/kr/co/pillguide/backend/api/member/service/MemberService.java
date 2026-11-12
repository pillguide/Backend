package kr.co.pillguide.backend.api.member.service;

import kr.co.pillguide.backend.api.member.dto.MemberAdditionalRequestDTO;
import kr.co.pillguide.backend.api.member.dto.MemberProfileRequest;
import kr.co.pillguide.backend.api.member.dto.MemberProfileResponse;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import kr.co.pillguide.backend.common.exception.BadRequestException;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import jakarta.persistence.EntityManager;
import kr.co.pillguide.backend.common.response.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final EntityManager em;

    @Transactional(readOnly = true)
    public MemberProfileResponse getProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new UnauthorizedException("인증되지 않은 사용자입니다."));
        return MemberProfileResponse.from(member);
    }

    @Transactional
    public MemberProfileResponse updateProfile(JwtService.TokenClaims claims, MemberProfileRequest request) {
        if (request == null) throw new BadRequestException("수정할 프로필 정보가 필요합니다.");
        String name = request.name() == null ? null : request.name().trim();
        String email = request.email() == null ? null : request.email().trim();
        if (name != null && name.isEmpty()) throw new BadRequestException("이름을 입력해 주세요.");
        if (email != null && email.isEmpty()) throw new BadRequestException("이메일을 입력해 주세요.");
        if (name == null && email == null && request.gender() == null && request.birthDate() == null) {
            throw new BadRequestException("수정할 프로필 정보가 필요합니다.");
        }
        if (name != null && name.length() > 50) throw new BadRequestException("이름은 50자 이하여야 합니다.");
        if (email != null && email.length() > 100) throw new BadRequestException("이메일은 100자 이하여야 합니다.");
        Member member = MemberLocks.lockAuthenticated(em, claims);
        member.updateProfile(name, request.gender(), request.birthDate(), email);
        return MemberProfileResponse.from(member);
    }

    @Transactional
    public void updateAdditionalInfo(Long memberId, MemberAdditionalRequestDTO requestDTO) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException(ErrorStatus.NOT_FOUND_USER.getMessage())
                );

        // 이메일 없는 사용자 처리 (카카오)
        if (member.getEmail() == null && requestDTO.email() == null) {
            throw new kr.co.pillguide.backend.common.exception.BadRequestException("이메일 입력이 필요합니다.");
        }

        if (member.getEmail() == null) {
            member.updateEmail(requestDTO.email());
        }

        member.updateAdditionalInfo(requestDTO.gender(), requestDTO.birthDate());

    }
}
