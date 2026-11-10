package kr.co.pillguide.backend.api.member.service;

import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import kr.co.pillguide.backend.common.response.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import kr.co.pillguide.backend.common.exception.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.TreeSet;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class FcmTokenService {

    private final MemberRepository memberRepository;
    private final EntityManager em;

    @Transactional
    public void register(Long memberId, String token) {
        if (!memberRepository.existsById(memberId)) {
            throw new NotFoundException(
                    ErrorStatus.NOT_FOUND_USER.getMessage()
            );
        }

        Long previousOwner = memberRepository.findIdByFcmToken(token).orElse(null);
        var ids = new TreeSet<Long>();
        ids.add(memberId);
        if (previousOwner != null) {
            ids.add(previousOwner);
        }
        var locked = new HashMap<Long, Member>();
        for (Long id : ids) {
            locked.put(id, MemberLocks.lock(em, id));
        }
        Long currentOwner = memberRepository.findIdByFcmToken(token).orElse(null);
        if (currentOwner != null && !locked.containsKey(currentOwner)) {
            throw new ConflictException("기기 연결이 변경됐습니다. 다시 등록해 주세요.");
        }
        if (currentOwner != null && !currentOwner.equals(memberId)) {
            locked.get(currentOwner).clearFcmToken();
            memberRepository.flush();
        }
        locked.get(memberId).updateFcmToken(token);
        try {
            memberRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("기기가 다른 계정에 연결됐습니다. 다시 등록해 주세요.");
        }
    }

    @Transactional
    public void delete(Long memberId) {
        Member member = MemberLocks.lock(em, memberId);
        member.clearFcmToken();
    }

}
