package com.cakeshop.domain.member.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

import com.cakeshop.domain.member.dto.form.MemberAdminListType;
import com.cakeshop.domain.member.dto.form.MemberAdminSearchCondition;
import com.cakeshop.domain.member.dto.view.MemberAdminListRow;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberAdminMapperTests {

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String marker;

    @BeforeEach
    void setUp() {
        marker = "관리자목록-" + System.nanoTime();

        insertMember("active", "USER", MemberStatus.ACTIVE, 1);
        insertMember("suspended", "USER", MemberStatus.SUSPENDED, 2);
        insertMember("withdrawn", "USER", MemberStatus.WITHDRAWN, 3);
        insertMember("admin", "ADMIN", MemberStatus.ACTIVE, 4);
    }

    @Test
    void findAdminMembers_eachTab_returnsOnlyMatchingAccounts() {
        MemberAdminSearchCondition condition = condition(MemberAdminListType.MEMBERS);

        assertThat(memberMapper.countAdminMembers(condition)).isEqualTo(2);
        assertThat(memberMapper.findAdminMembers(condition, 10, 0))
                .extracting(MemberAdminListRow::status)
                .containsExactly(
                        MemberStatus.SUSPENDED,
                        MemberStatus.ACTIVE);

        condition.setListType(MemberAdminListType.WITHDRAWN);

        assertThat(memberMapper.findAdminMembers(condition, 10, 0))
                .singleElement()
                .satisfies(member ->
                        assertThat(member.status()).isEqualTo(MemberStatus.WITHDRAWN));

        condition.setListType(MemberAdminListType.ADMINS);

        assertThat(memberMapper.findAdminMembers(condition, 10, 0))
                .singleElement()
                .satisfies(member ->
                        assertThat(member.email()).contains("admin"));
    }

    @Test
    void findAdminMembers_statusAndPhoneKeyword_filtersMembers() {
        MemberAdminSearchCondition condition = condition(MemberAdminListType.MEMBERS);

        condition.setStatus(MemberStatus.SUSPENDED);
        condition.setKeyword("01000000002");

        List<MemberAdminListRow> members =
                memberMapper.findAdminMembers(condition, 10, 0);

        assertThat(members)
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.status()).isEqualTo(MemberStatus.SUSPENDED);
                    assertThat(member.phone()).isEqualTo("010-0000-0002");
                });
    }

    @Test
    void findAdminMembers_paging_ordersByNewestFirst() {
        MemberAdminSearchCondition condition = condition(MemberAdminListType.MEMBERS);

        assertThat(memberMapper.findAdminMembers(condition, 1, 0))
                .extracting(MemberAdminListRow::email)
                .containsExactly(email("suspended"));
        assertThat(memberMapper.findAdminMembers(condition, 1, 1))
                .extracting(MemberAdminListRow::email)
                .containsExactly(email("active"));
    }

    private MemberAdminSearchCondition condition(MemberAdminListType listType) {
        MemberAdminSearchCondition condition = new MemberAdminSearchCondition();

        condition.setListType(listType);
        condition.setKeyword(marker);

        return condition;
    }

    private void insertMember(
            String account,
            String role,
            MemberStatus status,
            int month) {
        LocalDateTime createdAt =
                LocalDateTime.of(2026, month, 1, 10, 0);

        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email,
                    password,
                    nickname,
                    phone,
                    role,
                    status,
                    name,
                    birth_date,
                    created_at,
                    updated_at,
                    withdrawn_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                email(account),
                "encoded-password",
                account,
                "010-0000-000" + month,
                role,
                status.name(),
                marker + " " + account,
                LocalDate.of(2000, month, 1),
                createdAt,
                createdAt,
                status == MemberStatus.WITHDRAWN
                        ? createdAt.plusDays(1)
                        : null);
    }

    private String email(String account) {
        return marker + "-" + account + "@example.com";
    }
}
