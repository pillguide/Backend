package kr.co.pillguide.backend.api.reminder.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import kr.co.pillguide.backend.api.reminder.dto.ReminderRequest;
import kr.co.pillguide.backend.api.reminder.dto.MedicationResponses.*;
import kr.co.pillguide.backend.api.reminder.service.MedicationService;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import kr.co.pillguide.backend.common.response.ApiResponse;
import kr.co.pillguide.backend.common.response.SuccessStatus;
import kr.co.pillguide.backend.common.security.SecurityMember;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/medication")
@Tag(name = "복약 관리", description = "복약 알림 설정 및 복약 체크 기록 API")
public class MedicationController {

    private final MedicationService service;

    // 알림 활성화 여부 변경 요청
    public record EnabledRequest(
            @NotNull Boolean enabled
    ) {
    }

    // 복약 완료·취소 요청
    public record CheckRequest(
            @NotNull Boolean taken
    ) {
    }

    // 선택한 알림들의 일괄 삭제 요청
    public record DeleteRequest(
            @NotEmpty
            @Size(max = 100)
            List<@NotNull @Positive Long> ids
    ) {
    }

    @Operation(summary = "내 복약 알림 목록 조회")
    @GetMapping("/reminders")
    public ResponseEntity<ApiResponse<List<Reminder>>> list(
            @AuthenticationPrincipal SecurityMember principal
    ) {
        return ApiResponse.success(
                SuccessStatus.MEDICATION_GET_SUCCESS,
                service.list(memberId(principal))
        );
    }

    @Operation(summary = "복약 알림 등록")
    @PostMapping("/reminders")
    public ResponseEntity<ApiResponse<Reminder>> create(
            @AuthenticationPrincipal SecurityMember principal,
            @Valid @RequestBody ReminderRequest request
    ) {
        return ApiResponse.success(
                SuccessStatus.MEDICATION_CREATE_SUCCESS,
                service.create(memberId(principal), request)
        );
    }

    @Operation(summary = "복약 알림 수정")
    @PutMapping("/reminders/{id}")
    public ResponseEntity<ApiResponse<Reminder>> update(
            @AuthenticationPrincipal SecurityMember principal,
            @PathVariable Long id,
            @Valid @RequestBody ReminderRequest request
    ) {
        return ApiResponse.success(
                SuccessStatus.MEDICATION_UPDATE_SUCCESS,
                service.update(memberId(principal), id, request)
        );
    }

    @Operation(summary = "복약 알림 활성화·비활성화")
    @PatchMapping("/reminders/{id}/enabled")
    public ResponseEntity<ApiResponse<Reminder>> enabled(
            @AuthenticationPrincipal SecurityMember principal,
            @PathVariable Long id,
            @Valid @RequestBody EnabledRequest request
    ) {
        return ApiResponse.success(
                SuccessStatus.MEDICATION_UPDATE_SUCCESS,
                service.enabled(
                        memberId(principal),
                        id,
                        request.enabled()
                )
        );
    }

    @Operation(summary = "복약 알림 개별 삭제")
    @DeleteMapping("/reminders/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal SecurityMember principal,
            @PathVariable Long id
    ) {
        service.delete(memberId(principal), List.of(id));

        return ApiResponse.successOnly(
                SuccessStatus.MEDICATION_DELETE_SUCCESS
        );
    }

    @Operation(summary = "선택한 복약 알림 일괄 삭제")
    @PostMapping("/reminders/bulk-delete")
    public ResponseEntity<ApiResponse<Void>> deleteMany(
            @AuthenticationPrincipal SecurityMember principal,
            @Valid @RequestBody DeleteRequest request
    ) {
        service.delete(memberId(principal), request.ids());

        return ApiResponse.successOnly(
                SuccessStatus.MEDICATION_DELETE_SUCCESS
        );
    }

    @Operation(
            summary = "날짜별 복약 기록 조회",
            description = "date는 yyyy-MM-dd 형식으로 입력합니다."
    )
    @GetMapping("/records")
    public ResponseEntity<ApiResponse<Day>> records(
            @AuthenticationPrincipal SecurityMember principal,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return ApiResponse.success(
                SuccessStatus.MEDICATION_GET_SUCCESS,
                service.day(memberId(principal), date)
        );
    }

    @Operation(
            summary = "복약 완료·취소",
            description = "taken=true는 완료, false는 완료 취소입니다."
    )
    @PutMapping("/records/{id}/check")
    public ResponseEntity<ApiResponse<Dose>> check(
            @AuthenticationPrincipal SecurityMember principal,
            @PathVariable Long id,
            @Valid @RequestBody CheckRequest request
    ) {
        return ApiResponse.success(
                SuccessStatus.MEDICATION_UPDATE_SUCCESS,
                service.check(
                        memberId(principal),
                        id,
                        request.taken()
                )
        );
    }

    @Operation(
            summary = "주간 복약 기록 조회",
            description = "입력한 날짜가 포함된 월요일~일요일의 기록을 조회합니다."
    )
    @GetMapping("/records/weekly")
    public ResponseEntity<ApiResponse<Week>> weekly(
            @AuthenticationPrincipal SecurityMember principal,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return ApiResponse.success(
                SuccessStatus.MEDICATION_GET_SUCCESS,
                service.week(memberId(principal), date)
        );
    }

    @Operation(
            summary = "홈 화면 복약 요약",
            description = "오늘 진행률, 연속 완료 일수, 다음 알림, 오늘 체크리스트를 조회합니다."
    )
    @GetMapping("/home")
    public ResponseEntity<ApiResponse<Home>> home(
            @AuthenticationPrincipal SecurityMember principal
    ) {
        return ApiResponse.success(
                SuccessStatus.MEDICATION_GET_SUCCESS,
                service.home(memberId(principal))
        );
    }

    // 회원 ID는 요청 본문이 아닌 인증된 사용자에게서 가져옴
    private Long memberId(SecurityMember principal) {
        if (principal == null) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }

        return principal.getMemberId();
    }
}