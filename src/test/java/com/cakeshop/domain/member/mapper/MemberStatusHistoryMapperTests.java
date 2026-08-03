package com.cakeshop.domain.member.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.cakeshop.domain.member.dto.view.MemberStatusHistoryView;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.entity.MemberStatusAction;
import com.cakeshop.domain.member.entity.MemberStatusHistory;
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
class MemberStatusHistoryMapperTests {

    @Autowired
    private MemberStatusHistoryMapper memberStatusHistoryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;
    private Long adminId;

    @BeforeEach
    void setUp() {
        String marker = String.valueOf(System.nanoTime());

        memberId = insertMember("history-member-" + marker, "USER");
        adminId = insertMember("history-admin-" + marker, "ADMIN");
    }

    @Test
    void insertAndFindByMemberId_multipleChanges_returnsNewestFirst() {
        insertHistory(
                MemberStatusAction.SUSPEND,
                MemberStatus.ACTIVE,
                MemberStatus.SUSPENDED,
                "정지 사유");
        insertHistory(
                MemberStatusAction.ACTIVATE,
                MemberStatus.SUSPENDED,
                MemberStatus.ACTIVE,
                "해제 사유");

        List<MemberStatusHistoryView> histories =
                memberStatusHistoryMapper.findByMemberId(memberId);

        assertThat(histories)
                .extracting(MemberStatusHistoryView::action)
                .containsExactly(
                        MemberStatusAction.ACTIVATE,
                        MemberStatusAction.SUSPEND);
        assertThat(histories.getFirst().reason()).isEqualTo("해제 사유");
        assertThat(histories.getFirst().processorLabel())
                .startsWith("history-admin-");
    }

    private void insertHistory(
            MemberStatusAction action,
            MemberStatus beforeStatus,
            MemberStatus afterStatus,
            String reason) {
        MemberStatusHistory history = new MemberStatusHistory();

        history.setMemberId(memberId);
        history.setAction(action);
        history.setBeforeStatus(beforeStatus);
        history.setAfterStatus(afterStatus);
        history.setReason(reason);
        history.setProcessedBy(adminId);

        assertThat(memberStatusHistoryMapper.insert(history)).isOne();
        assertThat(history.getId()).isNotNull();
    }

    private Long insertMember(String account, String role) {
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email,
                    password,
                    nickname,
                    phone,
                    role,
                    status,
                    name
                ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?)
                """,
                account + "@example.com",
                "encoded-password",
                account,
                "010-0000-0000",
                role,
                account);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?",
                Long.class,
                account + "@example.com");
    }
}
