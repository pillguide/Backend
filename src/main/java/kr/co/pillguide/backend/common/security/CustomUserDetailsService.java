package kr.co.pillguide.backend.common.security;

import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import kr.co.pillguide.backend.common.response.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String memberId) throws UsernameNotFoundException {

        Long id;
        try {
            id = Long.valueOf(memberId);
        } catch (NumberFormatException e) {
            throw new UsernameNotFoundException("잘못된 사용자 식별자입니다. : " + memberId);
        }

        return memberRepository.findById(id)
                .map(SecurityMember::new)
                .orElseThrow(() ->
                        new NotFoundException(
                                ErrorStatus.NOT_FOUND_USER.getMessage() + " : " + memberId
                        )
                );
    }
}
