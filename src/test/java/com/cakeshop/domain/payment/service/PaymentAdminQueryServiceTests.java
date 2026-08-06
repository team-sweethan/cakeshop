package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.payment.dto.form.PaymentAdminSearchCondition;
import com.cakeshop.domain.payment.dto.view.PaymentAdminListRow;
import com.cakeshop.domain.payment.dto.view.PaymentAdminSummaryView;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAdminQueryServiceTests {

    @Test
    void getPayments_selectedStatus_returnsSummaryAndFilteredRows() {
        PaymentMapper paymentMapper = Mockito.mock(PaymentMapper.class);
        PaymentAdminQueryService service = new PaymentAdminQueryService(paymentMapper);
        PaymentAdminSearchCondition condition = new PaymentAdminSearchCondition();
        condition.setStatus(PaymentStatus.CANCELED);
        PaymentAdminSummaryView summary = new PaymentAdminSummaryView(5, 2, 1, 2);
        List<PaymentAdminListRow> rows = List.of();
        when(paymentMapper.summarizePaymentsForAdmin()).thenReturn(summary);
        when(paymentMapper.findPaymentsForAdmin(PaymentStatus.CANCELED)).thenReturn(rows);

        var result = service.getPayments(condition);

        assertThat(result.selectedStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(result.summary()).isEqualTo(summary);
        assertThat(result.payments()).isSameAs(rows);
        verify(paymentMapper).findPaymentsForAdmin(PaymentStatus.CANCELED);
    }
}
