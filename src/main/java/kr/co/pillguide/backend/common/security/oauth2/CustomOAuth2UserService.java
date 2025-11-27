package kr.co.pillguide.backend.common.security.oauth2;

import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.common.security.SecurityMember;
import kr.co.pillguide.backend.common.response.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        Member member;

        OAuth2User oAuth2User = delegate.loadUser(userRequest);
        var p = OAuth2Attributes.extract(userRequest, oAuth2User);

        Optional<Member> found = memberRepository.findByOauthIdAndProvider(p.providerId(), p.provider());
        if (found.isPresent()) {
            member = found.get();
        } else if (p.email() != null) {
            Optional<Member> emailMember = memberRepository.findByEmail(p.email());
            if (emailMember.isPresent()) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(
                                "duplicate_email",
                                ErrorStatus.CONFLICT_OAUTH2_EMAIL.getMessage(),
                                null
                        )
                );
            }

            member = memberRepository.save(Member.newSocialMember(p.name(), p.email(), p.provider(), p.providerId()));
        } else {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            "email_not_provided",
                            ErrorStatus.BAD_REQUEST_CANNOT_RECEIVE_OAUTH2_EMAIL.getMessage(),
                            null
                    )
            );
        }

        return SecurityMember.from(member);
    }
}
