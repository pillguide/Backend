package kr.co.pillguide.backend.api.drug.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import kr.co.pillguide.backend.api.drug.dto.ScanDrugDetailResponse;
import kr.co.pillguide.backend.api.drug.dto.ScanSessionDetailResponse;
import kr.co.pillguide.backend.api.drug.dto.ScanSessionSummaryResponse;
import kr.co.pillguide.backend.api.drug.service.ScanService;
import kr.co.pillguide.backend.api.scan.service.ScanWorkflow;
import kr.co.pillguide.backend.common.response.ApiResponse;
import kr.co.pillguide.backend.common.response.SuccessStatus;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import kr.co.pillguide.backend.common.security.SecurityMember;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/scan")
public class ScanController {

    private final ScanService scanService;
    private final ScanWorkflow workflow;

    @Operation(summary = "사진 분석 및 스캔 이력 저장 API",
            description = "multipart file과 requestId(UUID)를 받아 실제 AI 결과, 검수된 참조자료 조합, 당시 검출을 함께 저장합니다. 정상 미검출도 이력 1건입니다. 같은 요청 ID와 사진은 기존 결과를 반환하고 사진이 다르면 409입니다. 원본 사진은 보관하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새 분석 및 이력 저장"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "동일 요청 재시도: 기존 결과 반환"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 사진 또는 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 없음, 만료 또는 로그아웃"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "같은 요청 ID에 다른 사진"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "사진 크기 제한 초과"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "지원하지 않거나 손상된 사진"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "저장 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "AI 응답 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "참조자료 미준비 또는 AI 일시 장애"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "504", description = "AI 응답 시간 초과")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ScanSessionDetailResponse>> scan(
            @RequestPart("file") MultipartFile file,
            @RequestParam("requestId") UUID requestId,
            @AuthenticationPrincipal SecurityMember securityMember,
            Authentication authentication) {
        if (securityMember == null || authentication == null
                || !(authentication.getDetails() instanceof JwtService.TokenClaims claims)
                || !securityMember.getMemberId().equals(claims.memberId())) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        ScanWorkflow.Outcome outcome = workflow.analyze(claims, requestId, file);
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        var body = ApiResponse.<ScanSessionDetailResponse>builder().status(status.value()).success(true)
                .message(outcome.created() ? "사진 분석 및 이력 저장 완료" : "기존 사진 분석 이력 조회")
                .data(outcome.result()).build();
        return ResponseEntity.status(status).body(body);
    }

    @Operation(summary = "로그인 회원의 스캔 세션 목록 조회 API")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ScanSessionSummaryResponse>>> getScanSessions(
            @AuthenticationPrincipal SecurityMember securityMember) {

        List<ScanSessionSummaryResponse> sessions =
                scanService.getScanSessions(securityMember.getMemberId());

        return ApiResponse.success(SuccessStatus.SCAN_SESSION_LIST_GET_SUCCESS, sessions);
    }

    @Operation(summary = "로그인 회원의 스캔 세션 상세 및 약 목록 조회 API",
            description = "당시 검출과 병용 확인 결과를 반환합니다. 원본 사진은 재표시할 수 없으며 약 상세는 별도 경로에서 현재 자료를 조회합니다.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "이력이 없거나 다른 회원 소유")})
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<ScanSessionDetailResponse>> getScanSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal SecurityMember securityMember) {

        ScanSessionDetailResponse session =
                scanService.getScanSession(securityMember.getMemberId(), sessionId);

        return ApiResponse.success(SuccessStatus.SCAN_SESSION_GET_SUCCESS, session);
    }

    @Operation(summary = "로그인 회원의 스캔 세션 내 개별 약 상세 정보 조회 API",
            description = "해당 이력에 포함된 약의 현재 상세 정보를 반환합니다.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "이력 또는 약이 없거나 다른 회원 소유")})
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
