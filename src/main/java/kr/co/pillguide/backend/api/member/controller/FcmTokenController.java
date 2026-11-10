package kr.co.pillguide.backend.api.member.controller;

import jakarta.validation.Valid;
import kr.co.pillguide.backend.api.member.dto.FcmTokenRequest;
import kr.co.pillguide.backend.api.member.service.FcmTokenService;
import kr.co.pillguide.backend.common.response.ApiResponse;
import kr.co.pillguide.backend.common.response.SuccessStatus;
import kr.co.pillguide.backend.common.security.SecurityMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member/fcm-token")
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    @PutMapping
    public ResponseEntity<ApiResponse<Void>> register(
            @AuthenticationPrincipal SecurityMember securityMember,
            @Valid @RequestBody FcmTokenRequest request
    ) {
        fcmTokenService.register(
                securityMember.getMemberId(),
                request.token()
        );

        return ApiResponse.successOnly(
                SuccessStatus.MEMBER_DEVICE_FCM_TOKEN_REGISTER_SUCCESS
        );
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal SecurityMember securityMember
    ) {
        fcmTokenService.delete(securityMember.getMemberId());

        return ApiResponse.successOnly(
                SuccessStatus.MEMBER_DEVICE_FCM_TOKEN_DELETE_SUCCESS
        );
    }
}