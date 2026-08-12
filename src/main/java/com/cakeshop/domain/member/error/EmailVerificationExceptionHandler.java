package com.cakeshop.domain.member.error;

import com.cakeshop.domain.member.controller.EmailVerificationController;
import com.cakeshop.domain.member.dto.view.EmailVerificationResponse;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import com.cakeshop.global.error.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = EmailVerificationController.class)
public class EmailVerificationExceptionHandler {

    // 이메일 인증 업무 오류 응답
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<EmailVerificationResponse> handleBusiness(BusinessException exception) {
        return response(exception.getErrorCode());
    }

    // 이메일 인증 요청값 검증 오류 응답
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<EmailVerificationResponse> handleValidation() {
        return response(CommonErrorCode.INVALID_INPUT);
    }

    private ResponseEntity<EmailVerificationResponse> response(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.status())
                .body(EmailVerificationResponse.failure(errorCode.code(), errorCode.message()));
    }
}
