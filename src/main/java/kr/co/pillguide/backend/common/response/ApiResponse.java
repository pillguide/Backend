package kr.co.pillguide.backend.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.ResponseEntity;

@Builder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final int status;
    private final boolean success;
    private final String message;
    private T data;

    /***
     * 성공 응답 시 데이터와 함께 HTTP 상태 코드와 메시지를 포함한 ResponseEntity 생성
     *
     * @param status 성공 응답 코드 SuccessStatus enum
     * @param data 응답 본문에 포함할 데이터
     * @param <T> 응답 데이터 타입
     * @return HTTP 상태 코드와 함께 ApiResponse를 감싼 ResponseEntity
     */
    public static <T> ResponseEntity<ApiResponse<T>> success(SuccessStatus status, T data) {
        ApiResponse<T> response = ApiResponse.<T>builder()
                .status(status.getStatusCode())
                .success(true)
                .message(status.getMessage())
                .data(data)
                .build();
        return ResponseEntity.status(status.getStatusCode()).body(response);
    }

    /**
     * 데이터 없이 성공 여부와 메시지만 포함한 ApiResponse를 ResponseEntity로 생성
     *
     * @param status 성공 응답 상태를 나타내는 SuccessStatus enum
     * @return HTTP 상태 코드와 함께 ApiResponse<Void>를 감싼 ResponseEntity
     */
    public static ResponseEntity<ApiResponse<Void>> successOnly(SuccessStatus status) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .status(status.getStatusCode())
                .success(true)
                .message(status.getMessage())
                .build();
        return ResponseEntity.status(status.getStatusCode()).body(response);
    }

    /**
     * 실패 응답을 간단히 ApiResponse 객체로 생성. 데이터는 포함하지 않습니다.
     *
     * @param status HTTP 상태 코드를 나타내는 정수 값
     * @param message 실패 사유나 에러 메시지 문자열
     * @return 실패 정보를 담은 ApiResponse<Void> 객체
     */
    public static ApiResponse<Void> fail(int status, String message) {
        return ApiResponse.<Void>builder()
                .status(status)
                .success(false)
                .message(message)
                .build();
    }

    /**
     * ErrorStatus enum을 기반으로 실패 응답을 ApiResponse 객체로 생성. 데이터는 포함하지 않습니다.
     *
     * @param status 실패 상태를 나타내는 ErrorStatus enum
     * @return 실패 정보를 담은 ApiResponse<Void> 객체
     */
    public static ApiResponse<Void> failOnly(ErrorStatus status) {
        return ApiResponse.<Void>builder()
                .status(status.getStatusCode())
                .success(false)
                .message(status.getMessage())
                .build();
    }
}
