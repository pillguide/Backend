package kr.co.pillguide.backend.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends BaseException {

  public ConflictException() {
    super(HttpStatus.CONFLICT);
  }

  public ConflictException(String message) {
    super(HttpStatus.CONFLICT, message);
  }
}
