package com.cakeshop.domain.member.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ******************************
 * 작성자 : 주환
 * 담당자 : 수민
 * 작성일 : 2026-08-11
 * 기능 : 주문 관리자 권한 조회 SQL 검증
 * 설명 : 역할과 상태를 함께 확인해 세션의 오래된 ADMIN 권한을 신뢰하지 않도록 검증한다.
 * ******************************
 */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberOrderMapperTests {

    @Autowired
    private MemberOrderMapper memberOrderMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void existsActiveAdmin_requiresBothAdminRoleAndActiveStatus() {
        long activeAdminId = insertMember("ADMIN", MemberStatus.ACTIVE);
        long suspendedAdminId = insertMember("ADMIN", MemberStatus.SUSPENDED);
        long activeUserId = insertMember("USER", MemberStatus.ACTIVE);

        assertThat(memberOrderMapper.existsActiveAdmin(activeAdminId)).isTrue();
        assertThat(memberOrderMapper.existsActiveAdmin(suspendedAdminId)).isFalse();
        assertThat(memberOrderMapper.existsActiveAdmin(activeUserId)).isFalse();
    }

    private long insertMember(String role, MemberStatus status) {
        String email = "member-order-" + System.nanoTime() + "@example.com";
        jdbcTemplate.update(
                """
                INSERT INTO members (email, password, name, nickname, phone, role, status)
                VALUES (?, 'encoded-password', '주문관리자', '주문관리자', '010-0000-0000', ?, ?)
                """,
                email,
                role,
                status.name());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?", Long.class, email);
    }
}
