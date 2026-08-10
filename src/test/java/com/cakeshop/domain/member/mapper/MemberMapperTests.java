package com.cakeshop.domain.member.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cakeshop.domain.member.dto.view.MemberSummaryView;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
        assertThat(member.getBirthDate()).isNull();
        assertThat(member.getPhone()).isEqualTo("010-0000-0000");
    }

    @Test
    void existsActiveMember_returnsTrueOnlyForCurrentActiveMember() {
        Long activeId = insertMember(uniqueEmail("active-check"), MemberStatus.ACTIVE);
        Long withdrawnId = insertMember(
                uniqueEmail("withdrawn-check"),
                MemberStatus.WITHDRAWN
        );

        assertThat(memberMapper.existsActiveMember(activeId)).isTrue();
        assertThat(memberMapper.existsActiveMember(withdrawnId)).isFalse();
        assertThat(memberMapper.existsActiveMember(Long.MAX_VALUE)).isFalse();
    }

    @Test
    void findSummaryByMemberId_existingMember_returnsPublicFields() {
        Long memberId = insertMember(uniqueEmail("summary"), MemberStatus.SUSPENDED);

        assertThat(memberMapper.findSummaryByMemberId(memberId))
                .hasValueSatisfying(summary -> {
                    assertThat(summary.id()).isEqualTo(memberId);
                    assertThat(summary.name()).isEqualTo("매퍼 테스트");
                    assertThat(summary.role()).isEqualTo("USER");
                    assertThat(summary.status()).isEqualTo(MemberStatus.SUSPENDED);
                });
    }

    @Test
    void findSummaryByMemberId_missingMember_returnsEmpty() {
        assertThat(memberMapper.findSummaryByMemberId(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void findAllSummaries_existingMembers_returnsPublicFieldsInIdOrder() {
        Long firstId = insertMember(uniqueEmail("summary-all-first"), MemberStatus.ACTIVE);
        Long secondId = insertMember(uniqueEmail("summary-all-second"), MemberStatus.SUSPENDED);

        assertThat(memberMapper.findAllSummaries(1000, 0))
                .filteredOn(summary -> summary.id().equals(firstId) || summary.id().equals(secondId))
                .extracting(MemberSummaryView::id)
                .containsExactly(firstId, secondId);
        assertThat(memberMapper.countAllMembers()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void findEmailsByMemberInfo_matchingMembers_returnsOnlyActiveEmails() {
        String firstEmail = uniqueEmail("recovery-first");
        String secondEmail = uniqueEmail("recovery-second");
        Long firstId = insertMember(firstEmail, MemberStatus.ACTIVE);
        Long secondId = insertMember(secondEmail, MemberStatus.WITHDRAWN);
        LocalDate birthDate = LocalDate.of(2000, 1, 15);
        jdbcTemplate.update(
                "UPDATE members SET birth_date = ?, phone = ? WHERE id IN (?, ?)",
                birthDate,
                "010-1234-5678",
                firstId,
                secondId);

        List<String> emails = memberMapper.findEmailsByMemberInfo(
                "매퍼 테스트",
                birthDate,
                "01012345678");

        assertThat(emails).containsExactly(firstEmail);
    }

    @Test
    void findEmailsByMemberInfo_mismatchedInfo_returnsEmptyList() {
        String email = uniqueEmail("recovery-mismatch");
        Long memberId = insertMember(email, MemberStatus.ACTIVE);
        jdbcTemplate.update(
                "UPDATE members SET birth_date = ? WHERE id = ?",
                LocalDate.of(2000, 1, 15),
                memberId);

        assertThat(memberMapper.findEmailsByMemberInfo(
                "다른 이름",
                LocalDate.of(2000, 1, 15),
                "01000000000")).isEmpty();
    }

    @Test
    void findPasswordRecoveryMember_matchingActiveMember_returnsCanonicalMember() {
        String email = uniqueEmail("password-recovery");
        Long memberId = insertMember(email, MemberStatus.ACTIVE);
        LocalDate birthDate = LocalDate.of(2000, 1, 15);
        jdbcTemplate.update(
                "UPDATE members SET birth_date = ?, phone = ? WHERE id = ?",
                birthDate,
                "010-1234-5678",
                memberId);

        assertThat(memberMapper.findPasswordRecoveryMember(
                email,
                "매퍼 테스트",
                birthDate,
                "01012345678"))
                .hasValueSatisfying(member -> {
                    assertThat(member.getId()).isEqualTo(memberId);
                    assertThat(member.getEmail()).isEqualTo(email);
                });
    }

    @Test
    void findPasswordRecoveryMember_withdrawnOrPasswordlessMember_returnsEmpty() {
        LocalDate birthDate = LocalDate.of(2000, 1, 15);
        String withdrawnEmail = uniqueEmail("password-recovery-withdrawn");
        Long withdrawnId = insertMember(withdrawnEmail, MemberStatus.WITHDRAWN);
        String passwordlessEmail = uniqueEmail("password-recovery-oauth");
        Long passwordlessId = insertMember(passwordlessEmail, MemberStatus.ACTIVE);
        jdbcTemplate.update(
                "UPDATE members SET birth_date = ?, phone = ? WHERE id IN (?, ?)",
                birthDate,
                "010-1234-5678",
                withdrawnId,
                passwordlessId);
        jdbcTemplate.update(
                "UPDATE members SET password = NULL WHERE id = ?",
                passwordlessId);

        assertThat(memberMapper.findPasswordRecoveryMember(
                withdrawnEmail,
                "매퍼 테스트",
                birthDate,
                "01012345678")).isEmpty();
        assertThat(memberMapper.findPasswordRecoveryMember(
                passwordlessEmail,
                "매퍼 테스트",
                birthDate,
                "01012345678")).isEmpty();
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
        LocalDate birthDate = LocalDate.of(2000, 1, 15);
        jdbcTemplate.update(
                "UPDATE members SET birth_date = ? WHERE id = ?",
                birthDate,
                memberId);
        Member updateMember = new Member();
        updateMember.setId(memberId);
        updateMember.setName("수정된 이름");
        updateMember.setNickname("수정된닉네임");
        updateMember.setPhone("010-9876-5432");

        int updatedRows = memberMapper.update(updateMember);

        Member updatedMember =
                memberMapper.findByEmail(email).orElseThrow();

        assertThat(updatedRows).isEqualTo(1);
        assertThat(updatedMember.getName()).isEqualTo("수정된 이름");
        assertThat(updatedMember.getNickname()).isEqualTo("수정된닉네임");
        assertThat(updatedMember.getPhone()).isEqualTo("010-9876-5432");
        assertThat(updatedMember.getBirthDate()).isEqualTo(birthDate);
        assertThat(updatedMember.getUpdatedAt()).isAfter(UPDATED_AT);
    }

    @Test
    void updatePasswordForActiveMember_activeMember_updatesPassword() {
        String email = uniqueEmail("password-reset");
        Long memberId = insertMember(email, MemberStatus.ACTIVE);

        int updatedRows =
                memberMapper.updatePasswordForActiveMember(memberId, "new-encoded-password");

        assertThat(updatedRows).isEqualTo(1);
        assertThat(memberMapper.findByEmail(email).orElseThrow().getPassword())
                .isEqualTo("new-encoded-password");
    }

    @Test
    void findActivePasswordForUpdate_activeMember_returnsCurrentPassword() {
        Long memberId = insertMember(
                uniqueEmail("current-password"),
                MemberStatus.ACTIVE);

        assertThat(memberMapper.findActivePasswordForUpdate(memberId))
                .contains("encoded-password");
    }

    @Test
    void findActivePasswordForUpdate_withdrawnMember_returnsEmpty() {
        Long memberId = insertMember(
                uniqueEmail("current-password-withdrawn"),
                MemberStatus.WITHDRAWN);

        assertThat(memberMapper.findActivePasswordForUpdate(memberId)).isEmpty();
    }

    @Test
    void updatePasswordForActiveMember_withdrawnMember_doesNotUpdatePassword() {
        String email = uniqueEmail("password-reset-withdrawn");
        Long memberId = insertMember(email, MemberStatus.WITHDRAWN);

        int updatedRows =
                memberMapper.updatePasswordForActiveMember(memberId, "new-encoded-password");

        assertThat(updatedRows).isZero();
        assertThat(memberMapper.findByEmail(email).orElseThrow().getPassword())
                .isEqualTo("encoded-password");
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
                .birthDate(LocalDate.of(2000, 1, 15))
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
        assertThat(insertedMember.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 15));
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
