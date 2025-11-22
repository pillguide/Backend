package kr.co.pillguide.backend.api.member.service;

import kr.co.pillguide.backend.api.member.dto.MemberInfoRequestDTO;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.entity.OAuthProvider;
import kr.co.pillguide.backend.api.member.entity.Role;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional
    public void updateInfo(Long memberId, MemberInfoRequestDTO request) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        member.updateAdditionalInfo(request.getGender(), request.getBirthDate());
    }

    @Transactional
    public Member getOrCreateSocialMember(String email,
                                          OAuthProvider provider,
                                          String oauthId,
                                          String name,
                                          String gender,
                                          LocalDate birthDate,
                                          String accessToken,
                                          String refreshToken) {

        Member member = memberRepository.findByOauthIdAndProvider(oauthId, provider)
                .orElse(null);

        if (member == null) {
            return memberRepository.save(
                    Member.builder()
                            .email(email)
                            .provider(provider)
                            .oauthId(oauthId)
                            .name(name)
                            .gender(gender)
                            .birthDate(birthDate)
                            .role(Role.USER)
                            .accessToken(accessToken)
                            .refreshToken(refreshToken)
                            .build()
            );
        }

        return member;
    }
}