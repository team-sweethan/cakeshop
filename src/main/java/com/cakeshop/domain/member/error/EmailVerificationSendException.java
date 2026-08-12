package com.cakeshop.domain.member.error;

import com.cakeshop.global.error.BusinessException;

public class EmailVerificationSendException extends BusinessException {

    public EmailVerificationSendException() {
        super(MemberErrorCode.EMAIL_VERIFICATION_SEND_FAILED);
    }
}
