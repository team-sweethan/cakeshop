package com.cakeshop.domain.member.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberMapperTests {

    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2025, 1, 1, 10, 0);
    private static final LocalDateTime UPDATED_AT =
            LocalDateTime.of(2025, 1, 2, 10, 0);

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findByEmail_existingMember_mapsStatusAndTimestamps() {
        String email = uniqueEmail("find");
        Long memberId = insertMember(email, MemberStatus.ACTIVE);

        Member member = memberMapper.findByEmail(email).orElseThrow();

        assertThat(member.getId()).isEqualTo(memberId);
        assertThat(member.getEmail()).isEqualTo(email);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(member.getUpdatedAt()).isEqualTo(UPDATED_AT);
        assertThat(member.getWithdrawnAt()).isNull();
    }

    @Test
    void withdrawById_activeMember_updatesStatusAndTimestamps() {
        String email = uniqueEmail("withdraw");
        Long memberId = insertMember(email, MemberStatus.ACTIVE);

        int updatedRows =
                memberMapper.withdrawById(memberId, MemberStatus.WITHDRAWN);

        Member withdrawnMember =
                memberMapper.findByEmail(email).orElseThrow();

        assertThat(updatedRows).isEqualTo(1);
        assertThat(withdrawnMember.getStatus())
                .isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(withdrawnMember.getWithdrawnAt()).isNotNull();
        assertThat(withdrawnMember.getUpdatedAt()).isAfter(UPDATED_AT);
    }

    @Test
    void withdrawById_withdrawnMember_returnsZeroWithoutChangingTimestamps() {
        String email = uniqueEmail("duplicate-withdraw");
        Long memberId = insertMember(email, MemberStatus.ACTIVE);
        memberMapper.withdrawById(memberId, MemberStatus.WITHDRAWN);
        Member firstWithdrawal =
                memberMapper.findByEmail(email).orElseThrow();

        int updatedRows =
                memberMapper.withdrawById(memberId, MemberStatus.WITHDRAWN);

        Member secondWithdrawal =
                memberMapper.findByEmail(email).orElseThrow();

        assertThat(updatedRows).isZero();
        assertThat(secondWithdrawal.getStatus())
                .isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(secondWithdrawal.getWithdrawnAt())
                .isEqualTo(firstWithdrawal.getWithdrawnAt());
        assertThat(secondWithdrawal.getUpdatedAt())
                .isEqualTo(firstWithdrawal.getUpdatedAt());
    }

    @Test
    void updateStatus_unsupportedStatus_rejectedByDatabaseConstraint() {
        Long memberId = insertMember(
                uniqueEmail("invalid-status"),
                MemberStatus.ACTIVE);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE members SET status = ? WHERE id = ?",
                "UNKNOWN",
                memberId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void update_existingMember_updatesFieldsAndDatabaseTimestamp() {
        String email = uniqueEmail("update");
        Long memberId = insertMember(email, MemberStatus.ACTIVE);
        Member updateMember = new Member();
        updateMember.setId(memberId);
        updateMember.setName("수정된 이름");
        updateMember.setPhone("010-9876-5432");

        int updatedRows = memberMapper.update(updateMember);

        Member updatedMember =
                memberMapper.findByEmail(email).orElseThrow();

        assertThat(updatedRows).isEqualTo(1);
        assertThat(updatedMember.getName()).isEqualTo("수정된 이름");
        assertThat(updatedMember.getPhone()).isEqualTo("010-9876-5432");
        assertThat(updatedMember.getUpdatedAt()).isAfter(UPDATED_AT);
    }

    @Test
    void join_validMember_insertsMemberWithActiveDefaultStatus() {
        String email = uniqueEmail("join");
        Member member = Member.builder()
                .email(email)
                .password("encoded-password")
                .name("가입 회원")
                .nickname("신규회원")
                .phone("010-1234-5678")
                .role("USER")
                .build();

        int insertedRows = memberMapper.join(member);

        Member insertedMember =
                memberMapper.findByEmail(email).orElseThrow();

        assertThat(insertedRows).isEqualTo(1);
        assertThat(insertedMember.getEmail()).isEqualTo(email);
        assertThat(insertedMember.getPassword())
                .isEqualTo("encoded-password");
        assertThat(insertedMember.getName()).isEqualTo("가입 회원");
        assertThat(insertedMember.getNickname()).isEqualTo("신규회원");
        assertThat(insertedMember.getPhone()).isEqualTo("010-1234-5678");
        assertThat(insertedMember.getRole()).isEqualTo("USER");
        assertThat(insertedMember.getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
        assertThat(insertedMember.getCreatedAt()).isNotNull();
        assertThat(insertedMember.getUpdatedAt()).isNotNull();
    }

    private Long insertMember(String email, MemberStatus status) {
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
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                email,
                "encoded-password",
                "mapper-test",
                "010-0000-0000",
                "USER",
                status.name(),
                "매퍼 테스트",
                CREATED_AT,
                UPDATED_AT
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?",
                Long.class,
                email
        );
    }

    private String uniqueEmail(String scenario) {
        return scenario + "-" + System.nanoTime() + "@example.com";
    }
}
