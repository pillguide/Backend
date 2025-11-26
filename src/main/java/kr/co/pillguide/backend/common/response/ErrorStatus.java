package kr.co.pillguide.backend.common.response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public enum ErrorStatus {

    /// 400 BAD REQUEST
    BAD_REQUEST_MISSING_PARAM(HttpStatus.BAD_REQUEST, "요청 값이 입력되지 않았습니다."),
    BAD_REQUEST_MISSING_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "필수 입력값이 누락되었습니다."),
    BAD_REQUEST_DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "이미 사용 중인 이메일입니다."),
    BAD_REQUEST_INVALID_EMAIL(HttpStatus.BAD_REQUEST, "잘못된 이메일입니다."),
    BAD_REQUEST_VALID_FAILED(HttpStatus.BAD_REQUEST, "DTO 유효성 검증에 실패했습니다."),
    BAD_REQUEST_NOT_SUPPORTED_MEDIA_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 미디어 타입입니다."),
    BAD_REQUEST_INVALID_IMAGE_SIZE(HttpStatus.BAD_REQUEST, "이미지 파일 크기가 15MB 보다 큽니다."),
    BAD_REQUEST_INVALID_VIDEO_SIZE(HttpStatus.BAD_REQUEST, "동영상 파일 크기가 100MB 보다 큽니다."),
    BAD_REQUEST_NOT_SUPPORTED_DOMAIN(HttpStatus.BAD_REQUEST, "지원하지 않는 도메인입니다."),
    BAD_REQUEST_CANNOT_RECEIVE_OAUTH2_EMAIL(HttpStatus.BAD_REQUEST, "소셜 로그인에서 이메일 정보를 받을 수 없습니다."),

	/// 401 UNAUTHORIZED
	UNAUTHORIZED_USER(HttpStatus.UNAUTHORIZED, "인증되지 않은 사용자입니다."),
	UNAUTHORIZED_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
	UNAUTHORIZED_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
	UNAUTHORIZED_UNSUPPORTED_TOKEN(HttpStatus.UNAUTHORIZED, "지원되지 않는 토큰입니다."),
	UNAUTHORIZED_EMPTY_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 비어있습니다."),
	UNAUTHORIZED_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호가 일치하지 않습니다."),
	UNAUTHORIZED_EMAIL_OR_PASSWORD(HttpStatus.UNAUTHORIZED, "이메일 혹은 비밀번호를 다시 확인하세요."),
	UNAUTHORIZED_TOKEN_REISSUE_FAILED(HttpStatus.UNAUTHORIZED, "토큰 재발급에 실패했습니다."),

    /// 403 FORBIDDEN
    FORBIDDEN_RESOURCE_ACCESS(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    /// 404 NOT FOUND
    NOT_FOUND_USER(HttpStatus.NOT_FOUND, "해당 유저를 찾을 수 없습니다."),
    NOT_FOUND_RESOURCE(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    NOT_FOUND_EMAIL(HttpStatus.NOT_FOUND, "해당 이메일을 찾을 수 없습니다."),
    NOT_FOUND_MEMBER(HttpStatus.NOT_FOUND, "해당 회원을 찾을 수 없습니다."),

	/// 409 CONFLICT
	CONFLICT_DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "중복된 리소스가 존재합니다."),
    CONFLICT_MEMBER_DEVICE_FCM_TOKEN(HttpStatus.CONFLICT, "이미 다른 사용자가 등록한 FCM 토큰입니다."),
    CONFLICT_OAUTH2_EMAIL(HttpStatus.CONFLICT, "이미 다른 소셜 계정으로 가입된 이메일입니다."),

	/// 415 UNSUPPORTED MEDIA TYPE
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 Content-Type 입니다."),

	/// 500 SERVER ERROR
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
	IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다."),
	IMAGE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 삭제에 실패했습니다."),

	/// 503 SERVICE UNAVAILABLE
	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "서버에 연결할 수 없습니다."),

	;

	private final HttpStatus httpStatus;
	private final String message;

	public int getStatusCode() {
		return this.httpStatus.value();
	}
}
