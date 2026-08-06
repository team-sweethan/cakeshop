package com.cakeshop.domain.payment.dto.form;

import com.cakeshop.domain.payment.entity.PaymentStatus;
import lombok.Getter;
import lombok.Setter;

/** 관리자 결제 내역의 결제 상태 검색 조건이다. */
@Getter
@Setter
public class PaymentAdminSearchCondition {

    private PaymentStatus status;
}
