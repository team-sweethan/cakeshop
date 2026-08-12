package com.cakeshop.domain.member.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.member.entity.SocialAccount;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SocialAccountMapperTests {

    @Autowired
    private SocialAccountMapper socialAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertAndFindMemberEmail_linkedAccount_returnsMemberEmail() {
        String email = "social-" + System.nanoTime() + "@example.com";
        Long memberId = insertMember(email);
        SocialAccount account = SocialAccount.builder()
                .memberId(memberId)
                .provider("GOOGLE")
                .providerId("google-" + System.nanoTime())
                .build();

        assertThat(socialAccountMapper.insert(account)).isOne();
        assertThat(account.getId()).isNotNull();
        assertThat(socialAccountMapper.findMemberEmail(
                account.getProvider(), account.getProviderId())).contains(email);
    }

    @Test
    void findMemberEmail_unknownProviderIdentity_returnsEmpty() {
        assertThat(socialAccountMapper.findMemberEmail(
                "GOOGLE", "missing-" + System.nanoTime())).isEmpty();
    }

    private Long insertMember(String email) {
        jdbcTemplate.update(
                """
                INSERT INTO members (email, password, nickname, phone, role, status, name)
                VALUES (?, NULL, ?, ?, 'USER', 'ACTIVE', ?)
                """,
                email,
                "소셜테스트",
                "010-0000-0000",
                "소셜 테스트");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?",
                Long.class,
                email);
    }
}
