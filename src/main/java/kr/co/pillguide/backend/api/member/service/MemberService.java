package kr.co.pillguide.backend.api.member.service;

import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.entity.OAuthProvider;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    public Member getOrCreateSocialMember(String email,
                                          OAuthProvider provider,
                                          String oauthId,
                                          String name,
                                          String gender,
                                          LocalDate birthDate,
                                          String accessToken,
                                          String refreshToken) {

        return memberRepository.findByOauthIdAndProvider(oauthId, provider)
                .orElseGet(() -> memberRepository.save(
                        Member.builder()
                                .email(email)
                                .provider(provider)
                                .oauthId(oauthId)
                                .name(name)
                                .gender(gender)
                                .birthDate(birthDate)
                                .accessToken(accessToken)
                                .refreshToken(refreshToken)
                                .build()
                ));
    }
}
