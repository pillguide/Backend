package kr.co.pillguide.backend.api.drug.controller;

import io.swagger.v3.oas.annotations.Operation;
import kr.co.pillguide.backend.api.drug.dto.ScanDrugDetailResponse;
import kr.co.pillguide.backend.api.drug.dto.ScanRequest;
import kr.co.pillguide.backend.api.drug.dto.ScanSessionDetailResponse;
import kr.co.pillguide.backend.api.drug.dto.ScanSessionSummaryResponse;
import kr.co.pillguide.backend.api.drug.service.ScanService;
import kr.co.pillguide.backend.common.response.ApiResponse;
import kr.co.pillguide.backend.common.response.SuccessStatus;
import kr.co.pillguide.backend.common.security.SecurityMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/scan")
public class ScanController {

    private final ScanService scanService;

    @Operation(summary = "스캔 세션 등록 API")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> scan(
            @RequestBody ScanRequest request,
            @AuthenticationPrincipal SecurityMember securityMember) {

        Long memberId = securityMember.getMemberId();

        Long sessionId = scanService.saveScanSession(memberId, request);

        return ApiResponse.success(SuccessStatus.DRUG_CREATE_SUCCESS, sessionId);
    }

    @Operation(summary = "로그인 회원의 스캔 세션 목록 조회 API")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ScanSessionSummaryResponse>>> getScanSessions(
            @AuthenticationPrincipal SecurityMember securityMember) {

        List<ScanSessionSummaryResponse> sessions =
                scanService.getScanSessions(securityMember.getMemberId());

        return ApiResponse.success(SuccessStatus.SCAN_SESSION_LIST_GET_SUCCESS, sessions);
    }

    @Operation(summary = "로그인 회원의 스캔 세션 상세 및 약 목록 조회 API")
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<ScanSessionDetailResponse>> getScanSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal SecurityMember securityMember) {

        ScanSessionDetailResponse session =
                scanService.getScanSession(securityMember.getMemberId(), sessionId);

        return ApiResponse.success(SuccessStatus.SCAN_SESSION_GET_SUCCESS, session);
    }

    @Operation(summary = "로그인 회원의 스캔 세션 내 개별 약 상세 정보 조회 API")
    @GetMapping("/{sessionId}/drugs/{drugId}")
    public ResponseEntity<ApiResponse<ScanDrugDetailResponse>> getScanDrug(
            @PathVariable Long sessionId,
            @PathVariable Long drugId,
            @AuthenticationPrincipal SecurityMember securityMember) {

        ScanDrugDetailResponse drug = scanService.getScanDrug(
                securityMember.getMemberId(), sessionId, drugId);

        return ApiResponse.success(SuccessStatus.SCAN_DRUG_GET_SUCCESS, drug);
    }
}