package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.payment.dto.form.PaymentAdminSearchCondition;
import com.cakeshop.domain.payment.dto.view.PaymentAdminListRow;
import com.cakeshop.domain.payment.dto.view.PaymentAdminListView;
import com.cakeshop.domain.payment.dto.view.PaymentAdminSummaryView;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 관리자가 실제 결제·취소 처리 결과를 조회하도록 목록을 구성한다. */
@Service
@RequiredArgsConstructor
public class PaymentAdminQueryService {

    private final PaymentMapper paymentMapper;

    @Transactional(readOnly = true)
    public PaymentAdminListView getPayments(PaymentAdminSearchCondition condition) {
        PaymentStatus selectedStatus = condition == null ? null : condition.getStatus();
        PaymentAdminSummaryView summary = paymentMapper.summarizePaymentsForAdmin();
        List<PaymentAdminListRow> payments = paymentMapper.findPaymentsForAdmin(selectedStatus);
        return new PaymentAdminListView(selectedStatus, summary, payments);
    }
}
