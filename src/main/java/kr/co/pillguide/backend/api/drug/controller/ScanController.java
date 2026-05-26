package kr.co.pillguide.backend.api.drug.controller;

import kr.co.pillguide.backend.api.drug.dto.ScanRequest;
import kr.co.pillguide.backend.api.drug.service.ScanService;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.common.response.ApiResponse;
import kr.co.pillguide.backend.common.response.SuccessStatus;
import kr.co.pillguide.backend.common.security.SecurityMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/scan")
public class ScanController {

    private final ScanService scanService;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> scan(
            @RequestBody ScanRequest request,
            @AuthenticationPrincipal SecurityMember securityMember) {

        Long memberId = securityMember.getMemberId();

        Long sessionId = scanService.saveScanSession(memberId, request);

        return ApiResponse.success(SuccessStatus.DRUG_CREATE_SUCCESS, sessionId);
    }
}