package kr.co.pillguide.backend.api.mydrug.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import kr.co.pillguide.backend.api.mydrug.dto.AddMyDrugsRequest;
import kr.co.pillguide.backend.api.mydrug.dto.MyDrugDetailResponse;
import kr.co.pillguide.backend.api.mydrug.dto.MyDrugItemResponse;
import kr.co.pillguide.backend.api.mydrug.dto.MyDrugPageResponse;
import kr.co.pillguide.backend.api.mydrug.service.MyDrugService;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import kr.co.pillguide.backend.common.response.ApiResponse;
import kr.co.pillguide.backend.common.security.SecurityMember;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/my-drugs")
@RequiredArgsConstructor
public class MyDrugController {
    private final MyDrugService service;

    @Operation(summary = "스캔에서 선택한 검출을 내 약에 추가",
            description = "본인 scanSessionId와 영구 detectionIds만 받습니다. 동일 품목 검출은 한 관계로 저장하며 기존 관계의 최초 등록 시각을 유지합니다. 스캔 이력과 복약 일정은 변경하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새 내 약 관계가 하나 이상 생성됨"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "선택한 품목이 모두 이미 등록됨"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "빈 선택, 잘못된 ID 또는 300개 초과"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 없음, 만료 또는 로그아웃"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "본인 스캔 또는 선택한 검출을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "저장 실패")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<List<MyDrugItemResponse>>> add(
            @RequestBody AddMyDrugsRequest request,
            @AuthenticationPrincipal SecurityMember principal,
            Authentication authentication) {
        var outcome = service.add(claims(principal, authentication), request);
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return respond(status, outcome.created() ? "내 약에 추가했습니다." : "이미 등록된 내 약입니다.",
                outcome.selected());
    }

    @Operation(summary = "내 약 목록 조회", description = "본인 관계만 최신 등록순으로 조회합니다. page 기본 0, size 기본 20·최대 100입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 약 페이지"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "페이지 범위 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 없음")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<MyDrugPageResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityMember principal) {
        return respond(HttpStatus.OK, "내 약 목록 조회 성공", service.list(memberId(principal), page, size));
    }

    @Operation(summary = "내 약 상세 조회", description = "관계의 최초 등록 시각과 현재 약 상세 정보를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 약 상세"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 관계 또는 다른 회원 소유")
    })
    @GetMapping("/{memberDrugId}")
    public ResponseEntity<ApiResponse<MyDrugDetailResponse>> detail(
            @PathVariable Long memberDrugId,
            @AuthenticationPrincipal SecurityMember principal) {
        return respond(HttpStatus.OK, "내 약 상세 조회 성공", service.detail(memberId(principal), memberDrugId));
    }

    @Operation(summary = "내 약에서 제거", description = "본인 관계만 삭제합니다. 스캔 이력, 약 참조자료, 복약 일정과 기록은 유지합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관계 제거 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 없음, 만료 또는 로그아웃"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 관계 또는 다른 회원 소유")
    })
    @DeleteMapping("/{memberDrugId}")
    public ResponseEntity<ApiResponse<Long>> delete(
            @PathVariable Long memberDrugId,
            @AuthenticationPrincipal SecurityMember principal,
            Authentication authentication) {
        return respond(HttpStatus.OK, "내 약에서 제거했습니다.",
                service.delete(claims(principal, authentication), memberDrugId));
    }

    private static Long memberId(SecurityMember principal) {
        if (principal == null) throw new UnauthorizedException("인증이 필요합니다.");
        return principal.getMemberId();
    }

    private static JwtService.TokenClaims claims(SecurityMember principal, Authentication authentication) {
        if (principal == null || authentication == null
                || !(authentication.getDetails() instanceof JwtService.TokenClaims claims)
                || !memberId(principal).equals(claims.memberId())) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        return claims;
    }

    private static <T> ResponseEntity<ApiResponse<T>> respond(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder().status(status.value())
                .success(true).message(message).data(data).build());
    }
}
