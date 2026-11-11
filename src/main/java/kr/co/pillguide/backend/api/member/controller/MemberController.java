package kr.co.pillguide.backend.api.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.pillguide.backend.api.member.dto.MemberAdditionalRequestDTO;
import kr.co.pillguide.backend.api.member.service.MemberService;
import kr.co.pillguide.backend.common.security.SecurityMember;
import kr.co.pillguide.backend.common.response.ApiResponse;
import kr.co.pillguide.backend.common.response.SuccessStatus;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Member", description = "Member 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member")
public class MemberController {

    private final MemberService memberService;
    private final JwtService jwtService;

    @Operation(
            summary = "선택적 프로필 정보 수정 API",
            description = "로그인 완료 조건이 아닌 기존 프로필 편집 API입니다. 이 API 호출 시 성별, 생년월일을 함께 전달합니다."
    )
    @PostMapping("/additional_info")
    public ResponseEntity<ApiResponse<Void>> updateMemberInfo(
            @AuthenticationPrincipal SecurityMember securityMember,
            @Valid @RequestBody MemberAdditionalRequestDTO requestDTO) {

        memberService.updateAdditionalInfo(securityMember.getMemberId(), requestDTO);

        return ApiResponse.successOnly(SuccessStatus.MEMBER_ADDITIONAL_INFO_POST_SUCCESS);
    }
}
