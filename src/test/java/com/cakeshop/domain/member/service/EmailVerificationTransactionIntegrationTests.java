package com.cakeshop.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.cakeshop.domain.member.entity.EmailVerificationPurpose;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.error.EmailVerificationSendException;
import com.cakeshop.domain.member.mapper.EmailVerificationMapper;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({EmailVerificationService.class, EmailVerificationTransactionIntegrationTests.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmailVerificationTransactionIntegrationTests {

    private static final String SUCCESS_EMAIL = "transaction-lock-success@example.com";
    private static final String FAILURE_EMAIL = "transaction-lock-failure@example.com";

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private EmailVerificationMapper emailVerificationMapper;

    @Autowired
    private MemberMapper memberMapper;

    @MockitoBean
    private EmailSender emailSender;

    @Test
    void sendSignupCode_transactionCompletion_releasesRequestLock() {
        emailVerificationService.sendSignupCode(SUCCESS_EMAIL);

        assertThat(emailVerificationMapper.acquireRequestLock(
                SUCCESS_EMAIL, EmailVerificationPurpose.SIGNUP, 0)).isOne();
        assertThat(emailVerificationMapper.releaseRequestLock(
                SUCCESS_EMAIL, EmailVerificationPurpose.SIGNUP)).isOne();
        verify(emailSender).sendVerificationCode(
                org.mockito.ArgumentMatchers.eq(SUCCESS_EMAIL),
                org.mockito.ArgumentMatchers.matches("\\d{6}"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendSignupCode_smtpFailure_commitsAttemptAndReleasesRequestLock() {
        doThrow(new EmailVerificationSendException())
                .when(emailSender)
                .sendVerificationCode(
                        org.mockito.ArgumentMatchers.eq(FAILURE_EMAIL),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> emailVerificationService.sendSignupCode(FAILURE_EMAIL))
                .isInstanceOf(EmailVerificationSendException.class);

        assertThat(emailVerificationMapper.findLatest(
                FAILURE_EMAIL, EmailVerificationPurpose.SIGNUP)).isPresent();
        assertThat(emailVerificationMapper.acquireRequestLock(
                FAILURE_EMAIL, EmailVerificationPurpose.SIGNUP, 0)).isOne();
        assertThat(emailVerificationMapper.releaseRequestLock(
                FAILURE_EMAIL, EmailVerificationPurpose.SIGNUP)).isOne();
    }

    @Test
    void sendPasswordResetCode_smtpFailure_rollsBackUndeliveredCode() {
        String email = "reset-" + UUID.randomUUID() + "@example.com";
        Member member = Member.builder()
                .email(email)
                .password("encoded-password")
                .name("reset member")
                .nickname("reset-" + UUID.randomUUID())
                .phone("010-1234-5678")
                .birthDate(LocalDate.of(2000, 1, 1))
                .role("USER")
                .build();
        memberMapper.join(member);
        doThrow(new EmailVerificationSendException())
                .when(emailSender)
                .sendVerificationCode(
                        org.mockito.ArgumentMatchers.eq(email),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> emailVerificationService.sendPasswordResetCode(email))
                .isInstanceOf(EmailVerificationSendException.class);

        assertThat(emailVerificationMapper.findLatest(
                email, EmailVerificationPurpose.PASSWORD_RESET)).isEmpty();
        assertThat(emailVerificationMapper.acquireRequestLock(
                email, EmailVerificationPurpose.PASSWORD_RESET, 0)).isOne();
        assertThat(emailVerificationMapper.releaseRequestLock(
                email, EmailVerificationPurpose.PASSWORD_RESET)).isOne();
    }

    @TestConfiguration
    static class Config {

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        Clock clock() {
            return Clock.system(ZoneId.of("Asia/Seoul"));
        }
    }
}
