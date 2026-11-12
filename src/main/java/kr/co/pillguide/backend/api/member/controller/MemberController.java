package kr.co.pillguide.backend.api.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.pillguide.backend.api.member.dto.MemberAdditionalRequestDTO;
import kr.co.pillguide.backend.api.member.dto.MemberProfileRequest;
import kr.co.pillguide.backend.api.member.dto.MemberProfileResponse;
import kr.co.pillguide.backend.api.member.service.MemberService;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import kr.co.pillguide.backend.common.security.SecurityMember;
import kr.co.pillguide.backend.common.response.ApiResponse;
import kr.co.pillguide.backend.common.response.SuccessStatus;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Member", description = "Member 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member")
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "내 프로필 조회", description = "인증된 회원의 이름, 성별, 생년월일, 이메일을 반환합니다. 미입력 값은 null입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 없음 또는 만료")
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberProfileResponse>> getProfile(
            @AuthenticationPrincipal SecurityMember principal) {
        if (principal == null) throw new UnauthorizedException("인증이 필요합니다.");
        return ApiResponse.success(SuccessStatus.MEMBER_INFO_GET_SUCCESS,
                memberService.getProfile(principal.getMemberId()));
    }

    @Operation(summary = "내 프로필 수정", description = "이름, 성별, 생년월일, 이메일 중 보낸 값만 수정합니다. 생략하거나 null인 필드는 유지하며 OAuth 제공자와 ID는 수정하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공, 최신 프로필 반환"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "수정 값 또는 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 없음, 만료 또는 로그아웃")
    })
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<MemberProfileResponse>> updateProfile(
            @AuthenticationPrincipal SecurityMember principal, Authentication authentication,
            @Valid @RequestBody MemberProfileRequest request) {
        if (principal == null || authentication == null
                || !(authentication.getDetails() instanceof JwtService.TokenClaims claims)
                || !principal.getMemberId().equals(claims.memberId())) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        return ApiResponse.success(SuccessStatus.MEMBER_INFO_UPDATE_SUCCESS,
                memberService.updateProfile(claims, request));
    }

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
