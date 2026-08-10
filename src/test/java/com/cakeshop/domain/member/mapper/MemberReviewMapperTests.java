package com.cakeshop.domain.member.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import com.cakeshop.domain.member.dto.view.MemberReviewView;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 수민
 * 작성일 : 2026-08-10
 * 기능 : 후기 작성자 표기용 회원 조회 SQL 검증
 * 설명 : 탈퇴 판정과 없는 ID 처리를 MariaDB Testcontainers 로 고정한다.
 *        실명과 닉네임을 다른 값으로 넣어 화면에 실명이 새는 회귀를 잡는다.
 * ******************************
 */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberReviewMapperTests {

    @Autowired
    private MemberReviewMapper memberReviewMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findMembersByIds_onlyWithdrawnIsMarked_andNicknameIsReturnedNotName() {
        Long activeId = insertMember("활동회원", MemberStatus.ACTIVE);
        Long suspendedId = insertMember("정지회원", MemberStatus.SUSPENDED);
        Long withdrawnId = insertMember("탈퇴회원", MemberStatus.WITHDRAWN);

        List<MemberReviewView> members = memberReviewMapper.findMembersByIds(
                List.of(activeId, suspendedId, withdrawnId));

        assertThat(members)
                .extracting(
                        MemberReviewView::id,
                        MemberReviewView::nickname,
                        MemberReviewView::withdrawn)
                .containsExactlyInAnyOrder(
                        tuple(activeId, "활동회원", false),
                        tuple(suspendedId, "정지회원", false),
                        tuple(withdrawnId, "탈퇴회원", true));
    }

    @Test
    void findMembersByIds_missingId_isOmittedWithoutError() {
        Long existingId = insertMember("남은회원", MemberStatus.ACTIVE);

        List<MemberReviewView> members =
                memberReviewMapper.findMembersByIds(List.of(existingId, Long.MAX_VALUE));

        assertThat(members).extracting(MemberReviewView::id).containsExactly(existingId);
    }

    @Test
    void findMemberIdsByNickname_matchesPartOfTheNickname() {
        Long matching = insertMember("케이크사랑", MemberStatus.ACTIVE);
        Long other = insertMember("빵순이", MemberStatus.ACTIVE);

        List<Long> ids = memberReviewMapper.findMemberIdsByNickname("이크사");

        assertThat(ids).contains(matching).doesNotContain(other);
    }

    @Test
    void findMemberIdsByNickname_escapedWildcard_matchesTheLiteralCharacter() {
        Long literal = insertMember("100%만족", MemberStatus.ACTIVE);
        Long other = insertMember("보통만족", MemberStatus.ACTIVE);

        List<Long> ids = memberReviewMapper.findMemberIdsByNickname("!%만족");

        assertThat(ids).contains(literal).doesNotContain(other);
    }

    @Test
    void findMemberIdsByNickname_withdrawnMember_isStillSearchable() {
        Long withdrawnId = insertMember("떠난회원", MemberStatus.WITHDRAWN);

        assertThat(memberReviewMapper.findMemberIdsByNickname("떠난회원")).contains(withdrawnId);
    }

    private Long insertMember(String nickname, MemberStatus status) {
        String email = "review-author-" + System.nanoTime() + "@example.com";
        jdbcTemplate.update(
                """
                INSERT INTO members (email, password, name, nickname, phone, role, status)
                VALUES (?, 'encoded-password', ?, ?, '010-0000-0000', 'USER', ?)
                """,
                email,
                nickname + "실명",
                nickname,
                status.name());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?", Long.class, email);
    }
}
