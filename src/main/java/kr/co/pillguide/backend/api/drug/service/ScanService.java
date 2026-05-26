package kr.co.pillguide.backend.api.drug.service;

import kr.co.pillguide.backend.api.drug.dto.ScanRequest;
import kr.co.pillguide.backend.api.drug.entity.*;
import kr.co.pillguide.backend.api.drug.repository.*;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import kr.co.pillguide.backend.common.response.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScanService {

    private final ScanSessionRepository scanSessionRepository;
    private final DrugRepository drugRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Long saveScanSession(Long memberId, ScanRequest request) {

        // 1. Member 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException(ErrorStatus.NOT_FOUND_USER.getMessage()));

        // 2. 세션 생성
        ScanSession session = new ScanSession(member);

        // 3. 약 리스트 추가
        for (String itemSeq : request.itemSeqList()) {

            Drug drug = drugRepository.findByCode(itemSeq)
                    .orElseThrow(() -> new NotFoundException(
                            ErrorStatus.NOT_FOUND_DRUG.getMessage() + " : " + itemSeq
                    ));

            ScanDrug scanDrug = new ScanDrug(session, drug);
            session.addDrug(scanDrug);
        }

        // 4. 저장
        scanSessionRepository.save(session);

        return session.getId();
    }
}