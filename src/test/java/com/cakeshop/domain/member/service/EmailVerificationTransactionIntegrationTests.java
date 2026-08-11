package com.cakeshop.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.cakeshop.domain.member.entity.EmailVerificationPurpose;
import com.cakeshop.domain.member.mapper.EmailVerificationMapper;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.time.Clock;
import java.time.ZoneId;
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

    private static final String EMAIL = "transaction-lock@example.com";

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private EmailVerificationMapper emailVerificationMapper;

    @MockitoBean
    private EmailSender emailSender;

    @Test
    void sendSignupCode_transactionCompletion_releasesRequestLock() {
        emailVerificationService.sendSignupCode(EMAIL);

        assertThat(emailVerificationMapper.acquireRequestLock(
                EMAIL, EmailVerificationPurpose.SIGNUP, 0)).isOne();
        assertThat(emailVerificationMapper.releaseRequestLock(
                EMAIL, EmailVerificationPurpose.SIGNUP)).isOne();
        verify(emailSender).sendVerificationCode(
                org.mockito.ArgumentMatchers.eq(EMAIL),
                org.mockito.ArgumentMatchers.matches("\\d{6}"),
                org.mockito.ArgumentMatchers.any());
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
