package kr.co.pillguide.backend.api.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.pillguide.backend.api.member.dto.MemberAdditionalRequestDTO;
import kr.co.pillguide.backend.api.member.service.MemberService;
import kr.co.pillguide.backend.common.security.SecurityMember;
import kr.co.pillguide.backend.common.response.ApiResponse;
import kr.co.pillguide.backend.common.response.SuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Member", description = "Member 관련 API.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member")
public class MemberController {

    private final MemberService memberService;

    @Operation(
            summary = "소셜 로그인 추가 정보 입력 API",
            description = "소셜 로그인 후 추가 정보를 입력합니다. <br>" +
            "필수 정보: 성별, 생년월일"
    )
    @PostMapping("/additional_info")
    public ResponseEntity<ApiResponse<Void>> updateMemberInfo(
            @AuthenticationPrincipal SecurityMember securityMember,
            @Valid @RequestBody MemberAdditionalRequestDTO requestDTO) {

        memberService.updateAdditionalInfo(securityMember.getUsername(), requestDTO);

        return ApiResponse.successOnly(SuccessStatus.MEMBER_ADDITIONAL_INFO_POST_SUCCESS);
    }
}
