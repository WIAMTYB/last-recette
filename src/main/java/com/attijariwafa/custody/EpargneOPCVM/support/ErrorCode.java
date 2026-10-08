package com.attijariwafa.custody.EpargneOPCVM.support;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR"),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND"),
    ASSET_NOT_FOUND(HttpStatus.BAD_REQUEST, "ASSET_NOT_FOUND"),
    ACCOUNT_RELATIONSHIP_NOT_FOUND(HttpStatus.BAD_REQUEST, "ACCOUNT_RELATIONSHIP_NOT_FOUND"),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION"),
    SCHEDULER_RUN_IN_PROGRESS(HttpStatus.CONFLICT, "SCHEDULER_RUN_IN_PROGRESS"),
    SCHEDULER_RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULER_RUN_NOT_FOUND"),
    EXTERNAL_SERVICE_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "EXTERNAL_SERVICE_RATE_LIMITED"),
    EXTERNAL_SERVICE_FAILURE(HttpStatus.BAD_GATEWAY, "EXTERNAL_SERVICE_FAILURE"),
    TECHNICAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "TECHNICAL_ERROR");

    private final HttpStatus httpStatus;
    private final String code;

    ErrorCode(HttpStatus httpStatus, String code) {
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }
}
