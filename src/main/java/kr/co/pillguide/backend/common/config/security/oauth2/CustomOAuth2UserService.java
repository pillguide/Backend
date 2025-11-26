package kr.co.pillguide.backend.common.config.security.oauth2;

import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.common.config.security.SecurityMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
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

        OAuth2User oAuth2User = delegate.loadUser(userRequest);
        var p = OAuth2Attributes.extract(userRequest, oAuth2User);

        Optional<Member> found = memberRepository.findByOauthIdAndProvider(p.oauthId(), p.provider());
        if (found.isEmpty() && p.email() != null) {
            found = memberRepository.findByEmail(p.email());
        }

        Member member = found.orElseGet(() ->
                memberRepository.save()
        );

        return SecurityMember.from(member);
    }
}
