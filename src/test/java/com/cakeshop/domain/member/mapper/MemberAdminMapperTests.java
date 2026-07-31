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

    @Test
    void findAdminMemberDetail_existingMember_returnsDetail() {
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?",
                Long.class,
                email("suspended"));

        assertThat(memberMapper.findAdminMemberDetail(memberId))
                .hasValueSatisfying(member -> {
                    assertThat(member.id()).isEqualTo(memberId);
                    assertThat(member.name()).isEqualTo(marker + " suspended");
                    assertThat(member.nickname()).isEqualTo("suspended");
                    assertThat(member.email()).isEqualTo(email("suspended"));
                    assertThat(member.phone()).isEqualTo("010-0000-0002");
                    assertThat(member.birthDate())
                            .isEqualTo(LocalDate.of(2000, 2, 1));
                    assertThat(member.role()).isEqualTo("USER");
                    assertThat(member.status())
                            .isEqualTo(MemberStatus.SUSPENDED);
                    assertThat(member.createdAt())
                            .isEqualTo(LocalDateTime.of(2026, 2, 1, 10, 0));
                    assertThat(member.updatedAt())
                            .isEqualTo(LocalDateTime.of(2026, 2, 1, 10, 0));
                    assertThat(member.suspendedAt())
                            .isEqualTo(LocalDateTime.of(2026, 2, 2, 10, 0));
                    assertThat(member.suspendedReason())
                            .isEqualTo("관리자 테스트 이용정지");
                    assertThat(member.withdrawnAt()).isNull();
                });
    }

    @Test
    void findAdminMemberDetail_withdrawnMember_mapsWithdrawnAt() {
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?",
                Long.class,
                email("withdrawn"));

        assertThat(memberMapper.findAdminMemberDetail(memberId))
                .hasValueSatisfying(member ->
                        assertThat(member.withdrawnAt())
                                .isEqualTo(
                                        LocalDateTime.of(
                                                2026, 3, 2, 10, 0)));
    }

    @Test
    void findAdminMemberDetail_nonexistentMember_returnsEmpty() {
        assertThat(memberMapper.findAdminMemberDetail(Long.MAX_VALUE))
                .isEmpty();
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
                    suspended_at,
                    suspended_reason,
                    created_at,
                    updated_at,
                    withdrawn_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                email(account),
                "encoded-password",
                account,
                "010-0000-000" + month,
                role,
                status.name(),
                marker + " " + account,
                LocalDate.of(2000, month, 1),
                status == MemberStatus.SUSPENDED
                        ? createdAt.plusDays(1)
                        : null,
                status == MemberStatus.SUSPENDED
                        ? "관리자 테스트 이용정지"
                        : null,
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
