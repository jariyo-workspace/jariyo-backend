package com.example.jariyo_backend.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
	AUTHENTICATION_REQUIRED("AUTHENTICATION_REQUIRED", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	INVALID_CREDENTIALS("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	INVALID_ACCESS_TOKEN("INVALID_ACCESS_TOKEN", HttpStatus.UNAUTHORIZED, "Access Token이 올바르지 않습니다."),
	ACCESS_TOKEN_EXPIRED("ACCESS_TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED, "Access Token이 만료되었습니다."),
	INVALID_REFRESH_TOKEN("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED, "Refresh Token이 올바르지 않습니다."),
	REFRESH_TOKEN_EXPIRED("REFRESH_TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED, "Refresh Token이 만료되었습니다."),
	REFRESH_TOKEN_REUSED("REFRESH_TOKEN_REUSED", HttpStatus.UNAUTHORIZED, "재사용된 Refresh Token입니다."),
	ACCESS_DENIED("ACCESS_DENIED", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	STORE_ACCESS_DENIED("STORE_ACCESS_DENIED", HttpStatus.FORBIDDEN, "해당 매장에 대한 접근 권한이 없습니다."),
	RESOURCE_NOT_OWNED_BY_USER("RESOURCE_NOT_OWNED_BY_USER", HttpStatus.FORBIDDEN, "본인의 리소스만 처리할 수 있습니다."),
	EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
	PHONE_NUMBER_ALREADY_EXISTS("PHONE_NUMBER_ALREADY_EXISTS", HttpStatus.CONFLICT, "이미 사용 중인 전화번호입니다."),
	IDEMPOTENCY_KEY_REQUIRED("IDEMPOTENCY_KEY_REQUIRED", HttpStatus.BAD_REQUEST, "Idempotency-Key 헤더가 필요합니다."),
	IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST", HttpStatus.CONFLICT, "같은 멱등성 키를 다른 요청에 사용할 수 없습니다."),
	WALK_IN_NOT_FOUND("WALK_IN_NOT_FOUND", HttpStatus.NOT_FOUND, "현장 대기 정보를 찾을 수 없습니다."),
	WALK_IN_NOT_ENABLED("WALK_IN_NOT_ENABLED", HttpStatus.CONFLICT, "현장 대기를 운영하지 않는 매장입니다."),
	WALK_IN_REGISTRATION_CLOSED("WALK_IN_REGISTRATION_CLOSED", HttpStatus.CONFLICT, "현재 현장 대기를 등록할 수 없습니다."),
	WALK_IN_ALREADY_REGISTERED("WALK_IN_ALREADY_REGISTERED", HttpStatus.CONFLICT, "이미 활성 현장 대기가 있습니다."),
	WALK_IN_INVALID_STATE("WALK_IN_INVALID_STATE", HttpStatus.CONFLICT, "현재 현장 대기 상태에서는 요청을 처리할 수 없습니다."),
	WALK_IN_ALREADY_CALLED("WALK_IN_ALREADY_CALLED", HttpStatus.CONFLICT, "이미 호출된 현장 대기입니다."),
	WALK_IN_CALL_EXPIRED("WALK_IN_CALL_EXPIRED", HttpStatus.CONFLICT, "호출 응답 시간이 만료되었습니다."),
	SERVICE_NOT_ACTIVE("SERVICE_NOT_ACTIVE", HttpStatus.CONFLICT, "활성 서비스가 아닙니다."),
	STAFF_NOT_AVAILABLE("STAFF_NOT_AVAILABLE", HttpStatus.CONFLICT, "해당 서비스 예약이 불가능한 직원입니다."),
	WAITLIST_NOT_FOUND("WAITLIST_NOT_FOUND", HttpStatus.NOT_FOUND, "예약 대기 정보를 찾을 수 없습니다."),
	WAITLIST_NOT_OWNED_BY_USER("WAITLIST_NOT_OWNED_BY_USER", HttpStatus.FORBIDDEN, "본인의 예약 대기만 처리할 수 있습니다."),
	WAITLIST_NOT_ENABLED("WAITLIST_NOT_ENABLED", HttpStatus.CONFLICT, "예약 대기를 운영하지 않는 매장입니다."),
	WAITLIST_DUPLICATED("WAITLIST_DUPLICATED", HttpStatus.CONFLICT, "같은 조건의 활성 예약 대기가 이미 있습니다."),
	WAITLIST_DATE_OUT_OF_RANGE("WAITLIST_DATE_OUT_OF_RANGE", HttpStatus.BAD_REQUEST, "예약 대기 가능 날짜 범위를 벗어났습니다."),
	INVALID_WAITLIST_TIME_RANGE("INVALID_WAITLIST_TIME_RANGE", HttpStatus.BAD_REQUEST, "예약 대기 가능 시간 범위가 올바르지 않습니다."),
	WAITLIST_INVALID_STATE("WAITLIST_INVALID_STATE", HttpStatus.CONFLICT, "현재 예약 대기 상태에서는 요청을 처리할 수 없습니다."),
	SLOT_OFFER_NOT_FOUND("SLOT_OFFER_NOT_FOUND", HttpStatus.NOT_FOUND, "빈자리 제안을 찾을 수 없습니다."),
	SLOT_OFFER_EXPIRED("SLOT_OFFER_EXPIRED", HttpStatus.CONFLICT, "빈자리 제안이 만료되었습니다."),
	SLOT_OFFER_ALREADY_ACCEPTED("SLOT_OFFER_ALREADY_ACCEPTED", HttpStatus.CONFLICT, "이미 수락된 빈자리 제안입니다."),
	SLOT_OFFER_ALREADY_DECLINED("SLOT_OFFER_ALREADY_DECLINED", HttpStatus.CONFLICT, "이미 거절된 빈자리 제안입니다."),
	SLOT_OFFER_ALREADY_ACTIVE("SLOT_OFFER_ALREADY_ACTIVE", HttpStatus.CONFLICT, "해당 슬롯에 이미 활성 빈자리 제안이 있습니다."),
	SLOT_OFFER_NO_LONGER_AVAILABLE("SLOT_OFFER_NO_LONGER_AVAILABLE", HttpStatus.CONFLICT, "빈자리가 더 이상 유효하지 않습니다."),
	RESERVATION_NOT_FOUND("RESERVATION_NOT_FOUND", HttpStatus.NOT_FOUND, "예약 정보를 찾을 수 없습니다."),
	RESERVATION_NOT_OWNED_BY_USER("RESERVATION_NOT_OWNED_BY_USER", HttpStatus.FORBIDDEN, "본인의 예약만 처리할 수 있습니다."),
	RESERVATION_ALREADY_CANCELLED("RESERVATION_ALREADY_CANCELLED", HttpStatus.CONFLICT, "이미 취소된 예약입니다."),
	RESERVATION_INVALID_STATE("RESERVATION_INVALID_STATE", HttpStatus.CONFLICT, "현재 예약 상태에서는 요청을 처리할 수 없습니다."),
	RESERVATION_CANCELLATION_DEADLINE_PASSED("RESERVATION_CANCELLATION_DEADLINE_PASSED", HttpStatus.CONFLICT, "취소 가능 시간이 지났습니다."),
	RESERVATION_SLOT_ALREADY_TAKEN("RESERVATION_SLOT_ALREADY_TAKEN", HttpStatus.CONFLICT, "이미 다른 예약이 해당 시간을 선점했습니다."),
	RESERVATION_OUTSIDE_BOOKING_WINDOW("RESERVATION_OUTSIDE_BOOKING_WINDOW", HttpStatus.BAD_REQUEST, "예약 가능 기간을 벗어났습니다."),
	RESERVATION_TOO_CLOSE_TO_START("RESERVATION_TOO_CLOSE_TO_START", HttpStatus.BAD_REQUEST, "예약 시작까지 남은 시간이 부족합니다."),
	CUSTOMER_HAS_OVERLAPPING_RESERVATION("CUSTOMER_HAS_OVERLAPPING_RESERVATION", HttpStatus.CONFLICT, "같은 시간대에 이미 예약이 있습니다."),
	CHECK_IN_ALREADY_COMPLETED("CHECK_IN_ALREADY_COMPLETED", HttpStatus.CONFLICT, "이미 체크인되었습니다."),
	SERVICE_SESSION_NOT_FOUND("SERVICE_SESSION_NOT_FOUND", HttpStatus.NOT_FOUND, "서비스 세션을 찾을 수 없습니다."),
	SERVICE_SESSION_ALREADY_STARTED("SERVICE_SESSION_ALREADY_STARTED", HttpStatus.CONFLICT, "이미 서비스가 시작되었습니다."),
	SERVICE_SESSION_ALREADY_COMPLETED("SERVICE_SESSION_ALREADY_COMPLETED", HttpStatus.CONFLICT, "이미 서비스가 완료되었습니다."),
	SERVICE_SESSION_INVALID_STATE("SERVICE_SESSION_INVALID_STATE", HttpStatus.CONFLICT, "현재 서비스 세션 상태에서는 요청을 처리할 수 없습니다."),
	INVALID_PASSWORD_FORMAT("INVALID_PASSWORD_FORMAT", HttpStatus.BAD_REQUEST, "비밀번호는 15자 이상 64자 이하여야 합니다."),
	REQUIRED_AGREEMENT_MISSING("REQUIRED_AGREEMENT_MISSING", HttpStatus.BAD_REQUEST, "필수 약관에 동의해야 합니다."),
	USER_NOT_FOUND("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
	USER_SUSPENDED("USER_SUSPENDED", HttpStatus.FORBIDDEN, "정지된 사용자입니다."),
	STORE_NOT_FOUND("STORE_NOT_FOUND", HttpStatus.NOT_FOUND, "매장을 찾을 수 없습니다."),
	STORE_NOT_ACTIVE("STORE_NOT_ACTIVE", HttpStatus.CONFLICT, "운영 중인 매장이 아닙니다."),
	STORE_POLICY_NOT_FOUND("STORE_POLICY_NOT_FOUND", HttpStatus.NOT_FOUND, "매장 예약 정책을 찾을 수 없습니다."),
	SERVICE_NOT_FOUND("SERVICE_NOT_FOUND", HttpStatus.NOT_FOUND, "서비스를 찾을 수 없습니다."),
	STAFF_NOT_FOUND("STAFF_NOT_FOUND", HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다."),
	FAILED_JOB_NOT_FOUND("FAILED_JOB_NOT_FOUND", HttpStatus.NOT_FOUND, "실패 작업을 찾을 수 없습니다."),
	INVALID_AVAILABILITY_RANGE("INVALID_AVAILABILITY_RANGE", HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다."),
	INVALID_PARTY_SIZE("INVALID_PARTY_SIZE", HttpStatus.BAD_REQUEST, "예약 인원 수가 서비스 정책과 맞지 않습니다."),
	UNAUTHORIZED("AUTH-401", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	FORBIDDEN("AUTH-403", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	NOT_FOUND("COMMON-404", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
	CONFLICT("COMMON-409", HttpStatus.CONFLICT, "요청이 현재 상태와 충돌합니다."),
	BAD_REQUEST("COMMON-400", HttpStatus.BAD_REQUEST, "요청값이 올바르지 않습니다."),
	INTERNAL_SERVER_ERROR("COMMON-500", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final String code;
	private final HttpStatus status;
	private final String message;

	ErrorCode(String code, HttpStatus status, String message) {
		this.code = code;
		this.status = status;
		this.message = message;
	}

	public String getCode() {
		return code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}
}
