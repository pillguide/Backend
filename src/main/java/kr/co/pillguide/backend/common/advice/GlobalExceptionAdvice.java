package kr.co.pillguide.backend.common.advice;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.ConstraintViolationException;
import kr.co.pillguide.backend.common.exception.BaseException;
import kr.co.pillguide.backend.common.response.ApiResponse;
import kr.co.pillguide.backend.common.response.ErrorStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionAdvice {

    // 커스텀 예외 처리 (ex. BAD_REQUEST, CONFLICT 등)
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse> handleCostumeException(BaseException ex) {

        log.warn("커스텀 예외 발생 - {}: {}", ex.getClass().getSimpleName(), ex.getMessage());

        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.fail(ex.getStatusCode(), ex.getMessage()));
    }

    // 글로벌 예외 처리 (ex. 명시되어 있지 않은 모든 예외)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGlobalException(Exception ex) {

        log.error("[handleGlobalException] 알 수 없는 오류 발생 : {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .body(ApiResponse.fail(ErrorStatus.INTERNAL_SERVER_ERROR.getStatusCode(),
                        ErrorStatus.INTERNAL_SERVER_ERROR.getMessage()));
    }

    // @Validated 어노테이션으로 검증 실패 (@RequestParam, @PathVariable 등)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse> handleConstraintViolation(ConstraintViolationException ex) {

        String errorMsg = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));

        log.warn("파라미터 검증 실패 - {}", errorMsg);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value())
                .body(ApiResponse.fail(ErrorStatus.BAD_REQUEST_VALID_FAILED.getStatusCode(), errorMsg));
    }

    // 파라미터 타입 불일치 (ex. String을 Long으로 변환 실패)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {

        String errorMsg = String.format("파라미터 '%s'의 값 '%s'을(를) %s 타입으로 변환할 수 없습니다",
                ex.getName(), ex.getValue(), ex.getRequiredType().getSimpleName());

        log.warn("파라미터 타입 변환 실패 - {}", errorMsg);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value())
                .body(ApiResponse.fail(ErrorStatus.BAD_REQUEST_MISSING_PARAM.getStatusCode(), errorMsg));
    }

    // 필수 요청 파라미터 누락 (ex. 리뷰 ID 없이 리뷰 조회)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {

        String errorMsg = ErrorStatus.BAD_REQUEST_MISSING_REQUIRED_FIELD.getMessage() + ": " + ex.getParameterName();
        log.warn("필수 파라미터 누락 - {}", errorMsg);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value())
                .body(ApiResponse.fail(ErrorStatus.BAD_REQUEST_MISSING_REQUIRED_FIELD.getStatusCode(), errorMsg));
    }

    // 잘못된 인자 전달 (ex. 숫자 필드에 문자열 입력)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse> handleIllegalArgumentException(IllegalArgumentException ex) {

        log.warn("잘못된 인자 - {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value())
                .body(ApiResponse.fail(ErrorStatus.BAD_REQUEST_MISSING_PARAM.getStatusCode(), ex.getMessage()));
    }

    // DTO 유효성 검증 실패 (ex. @NotBlank 필드를 비운 채로 요청)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {

        String errorMsg = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> String.format("%s: %s", error.getField(),
                        error.getDefaultMessage()))
                .collect(Collectors.joining(", "));

        log.warn("DTO 검증 실패 - {}", errorMsg);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value())
                .body(ApiResponse.fail(ErrorStatus.BAD_REQUEST_VALID_FAILED.getStatusCode(), errorMsg));
    }

    // DB에 존재하지 않는 리소스 접근/조작 시도 (ex. 존재하지 않은 리뷰 접근)
    @ExceptionHandler(EmptyResultDataAccessException.class)
    public ResponseEntity<ApiResponse> handleEmptyResultDataAccessException(EmptyResultDataAccessException ex) {

        log.warn("존재하지 않는 리소스 접근 - {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND.value())
                .body(ApiResponse.fail(ErrorStatus.NOT_FOUND_RESOURCE.getStatusCode(),
                        ErrorStatus.NOT_FOUND_RESOURCE.getMessage()));
    }

    // 지원하지 않는 Content-Type (ex. JSON만 지원하는데 XML로 요청)
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {

        String supportedType = ex.getSupportedMediaTypes().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));

        String errorMsg = "지원하지 않는 Content-Type입니다. 지원 타입: " + supportedType;
        log.warn("지원하지 않는 Content-Type - 요청: {}, 지원: {}", ex.getContentType(), supportedType);

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
                .body(ApiResponse.fail(ErrorStatus.UNSUPPORTED_MEDIA_TYPE.getStatusCode(), errorMsg));
    }
}
