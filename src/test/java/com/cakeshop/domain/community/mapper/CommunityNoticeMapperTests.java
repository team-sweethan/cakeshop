package com.cakeshop.domain.community.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.community.dto.query.NoticeListRow;
import com.cakeshop.domain.community.entity.NoticeStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

/** 공지 노출 조건을 실제 MariaDB로 확인한다. */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommunityNoticeMapperTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 10, 0);

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 2, 1, 9, 0);

    private static final int PAGE_SIZE = 20;

    private static final int NO_OFFSET = 0;

    @Autowired
    private CommunityNoticeMapper communityNoticeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long memberId;

    @BeforeEach
    void setUp() {
        memberId = insertMember("notice-mapper-" + System.nanoTime() + "@cakeshop.local");
    }

    @Test
    void selectVisibleNotices_keepsOpenAndCurrentOnly() {
        insertNotice("무기한", NoticeStatus.PUBLISHED, null, null);
        insertNotice("진행 중", NoticeStatus.PUBLISHED, NOW.minusDays(1), NOW.plusDays(1));
        insertNotice("예정", NoticeStatus.PUBLISHED, NOW.plusDays(1), NOW.plusDays(2));
        insertNotice("종료", NoticeStatus.PUBLISHED, NOW.minusDays(2), NOW.minusDays(1));
        insertNotice("삭제됨", NoticeStatus.DELETED, null, null);

        assertThat(visibleTitles(NOW)).containsExactlyInAnyOrder("무기한", "진행 중");
    }

    @Test
    void selectVisibleNotices_startBoundary_includesExactInstantAndExcludesJustBefore() {
        insertNotice("시작 경계", NoticeStatus.PUBLISHED, NOW, null);

        assertThat(visibleTitles(NOW)).containsExactly("시작 경계");
        assertThat(visibleTitles(NOW.minusNanos(1_000))).isEmpty();
    }

    @Test
    void selectVisibleNotices_endBoundary_excludesExactInstantAndIncludesJustBefore() {
        insertNotice("종료 경계", NoticeStatus.PUBLISHED, null, NOW);

        assertThat(visibleTitles(NOW)).isEmpty();
        assertThat(visibleTitles(NOW.minusNanos(1_000))).containsExactly("종료 경계");
    }

    @Test
    void selectVisibleNotices_ordersByEffectiveDateNotRegisteredAt() {
        insertNotice("노출 시작이 최근인 공지", NoticeStatus.PUBLISHED, NOW.minusHours(1), null);
        insertNotice("등록만 오래전에 한 공지", NoticeStatus.PUBLISHED, null, null);

        assertThat(visibleTitles(NOW))
                .containsExactly("노출 시작이 최근인 공지", "등록만 오래전에 한 공지");
    }

    @Test
    void selectVisibleNotices_appliesLimitAndOffset() {
        insertNotice("첫째", NoticeStatus.PUBLISHED, NOW.minusHours(1), null);
        insertNotice("둘째", NoticeStatus.PUBLISHED, NOW.minusHours(2), null);
        insertNotice("셋째", NoticeStatus.PUBLISHED, NOW.minusHours(3), null);

        assertThat(titlesOf(communityNoticeMapper.selectVisibleNotices(NOW, 2, NO_OFFSET)))
                .containsExactly("첫째", "둘째");
        assertThat(titlesOf(communityNoticeMapper.selectVisibleNotices(NOW, 2, 2)))
                .containsExactly("셋째");
    }

    @Test
    void countVisibleNotices_countsWhatTheListReturns() {
        insertNotice("보이는 공지", NoticeStatus.PUBLISHED, null, null);
        insertNotice("끝난 공지", NoticeStatus.PUBLISHED, null, NOW.minusDays(1));
        insertNotice("지운 공지", NoticeStatus.DELETED, null, null);

        assertThat(communityNoticeMapper.countVisibleNotices(NOW)).isEqualTo(1);
    }

    @Test
    void selectVisibleNoticeById_returnsVisibleNoticeWithContent() {
        long noticeId = insertNotice("본문 있는 공지", NoticeStatus.PUBLISHED, null, null);

        assertThat(communityNoticeMapper.selectVisibleNoticeById(noticeId, NOW))
                .isNotNull()
                .satisfies(row -> assertThat(row.content()).isEqualTo("본문"));
    }

    @Test
    void selectVisibleNoticeById_outOfPeriodOrDeleted_returnsNull() {
        long scheduled = insertNotice("예정", NoticeStatus.PUBLISHED, NOW.plusDays(1), null);
        long ended = insertNotice("종료", NoticeStatus.PUBLISHED, null, NOW.minusDays(1));
        long deleted = insertNotice("삭제됨", NoticeStatus.DELETED, null, null);

        assertThat(communityNoticeMapper.selectVisibleNoticeById(scheduled, NOW)).isNull();
        assertThat(communityNoticeMapper.selectVisibleNoticeById(ended, NOW)).isNull();
        assertThat(communityNoticeMapper.selectVisibleNoticeById(deleted, NOW)).isNull();
    }

    private List<String> visibleTitles(LocalDateTime now) {
        return titlesOf(communityNoticeMapper.selectVisibleNotices(now, PAGE_SIZE, NO_OFFSET));
    }

    private List<String> titlesOf(List<NoticeListRow> rows) {
        return rows.stream().map(NoticeListRow::title).toList();
    }

    private long insertNotice(
            String title, NoticeStatus status, LocalDateTime startsAt, LocalDateTime endsAt) {

        jdbcTemplate.update(
                """
                INSERT INTO community_notices (
                    title, content, status, starts_at, ends_at, created_by, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                title, "본문", status.name(), startsAt, endsAt, memberId, CREATED_AT);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertMember(String email) {
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, nickname, phone, role, status, name, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', ?, ?, ?)
                """,
                email, "encoded-password", "공지테스트", "010-0000-0000",
                "공지 테스트", CREATED_AT, CREATED_AT);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
