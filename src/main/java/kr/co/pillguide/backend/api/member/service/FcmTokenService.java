package kr.co.pillguide.backend.api.member.service;

import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import kr.co.pillguide.backend.common.response.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FcmTokenService {

    private final MemberRepository memberRepository;

    @Transactional
    public void register(Long memberId, String token) {
        if (!memberRepository.existsById(memberId)) {
            throw new NotFoundException(
                    ErrorStatus.NOT_FOUND_USER.getMessage()
            );
        }

        // 같은 기기의 이전 계정 연결 해제
        memberRepository.clearTokenFromOtherMembers(token, memberId);

        Member member = findMember(memberId);

        // 기존 토큰이 있으면 새 토큰으로 교체
        member.updateFcmToken(token);
        memberRepository.flush();
    }

    @Transactional
    public void delete(Long memberId) {
        Member member = findMember(memberId);
        member.clearFcmToken();
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorStatus.NOT_FOUND_USER.getMessage()
                ));
    }
}