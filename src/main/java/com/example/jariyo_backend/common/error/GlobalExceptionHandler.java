package com.example.jariyo_backend.common.error;

import com.example.jariyo_backend.common.api.ApiResponse;
import com.example.jariyo_backend.common.api.ResponseSupport;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
		ErrorCode errorCode = exception.getErrorCode();
		return ResponseSupport.error(errorCode, exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
		return ResponseSupport.error(ErrorCode.BAD_REQUEST);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
		String message = exception.getMostSpecificCause().getMessage();
		ErrorCode errorCode = message != null && (message.contains("uk_walk_in_active_customer")
			|| message.contains("uk_walk_in_active_guest_phone"))
			? ErrorCode.WALK_IN_ALREADY_REGISTERED
			: message != null && (message.contains("uk_slot_offer_pending_slot")
				|| message.contains("uk_slot_offer_pending_waitlist"))
				? ErrorCode.SLOT_OFFER_ALREADY_ACTIVE
				: ErrorCode.CONFLICT;
		return ResponseSupport.error(errorCode);
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, ServletRequestBindingException.class,
		MethodArgumentTypeMismatchException.class})
	public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
		return ResponseSupport.error(ErrorCode.BAD_REQUEST);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
		return ResponseSupport.error(ErrorCode.INTERNAL_SERVER_ERROR);
	}
}
