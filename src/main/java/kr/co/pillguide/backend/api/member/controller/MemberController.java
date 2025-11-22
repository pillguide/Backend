package kr.co.pillguide.backend.api.member.controller;

import kr.co.pillguide.backend.api.member.dto.MemberInfoRequestDTO;
import kr.co.pillguide.backend.api.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member")
public class MemberController {

    private final MemberService memberService;

    @PatchMapping("/info")
    public ResponseEntity<Void> updateMemberInfo(@AuthenticationPrincipal Long memberId,
                                                 @RequestBody MemberInfoRequestDTO request) {
        memberService.updateInfo(memberId, request);
        return ResponseEntity.ok().build();
    }
}
