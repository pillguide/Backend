package kr.co.pillguide.backend.common.advice;

import kr.co.pillguide.backend.api.member.controller.FcmTokenController;
import kr.co.pillguide.backend.api.reminder.controller.MedicationController;
import kr.co.pillguide.backend.common.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keep malformed medication/device input handling scoped to the PR's APIs. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {MedicationController.class, FcmTokenController.class})
public class MedicationRequestAdvice {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> malformedBody() {
        // Parser exceptions may contain the original token or medication data.
        return ResponseEntity.badRequest().body(ApiResponse.fail(400,
                "요청 본문의 JSON 형식과 필드 값을 확인해 주세요."));
    }
}
