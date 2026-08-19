package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.community.dto.form.CommentForm;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 댓글·답글 알림의 수신자와 트랜잭션 경계를 실제 DB로 검증한다. */
@SpringBootTest
@MariaDbIntegrationTest
class CommunityCommentNotificationTests {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

    @Autowired
    private CommunityCommentService communityCommentService;

    /*
     * 스파이로 두지 않는다. Mockito 스파이는 실제 메서드를 프록시를 지나지 않고 부르므로
     * REQUIRES_NEW 가 사라지고, 커밋 이후 발송이 아무도 커밋하지 않는 트랜잭션에 얹힌다.
     */
    @Autowired
    private CommunityCommentNotificationSender communityCommentNotificationSender;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private NotificationService notificationService;

    @MockitoSpyBean
    private CommunityCommentNotificationService communityCommentNotificationService;

    private long categoryId;
    private long postAuthorId;
    private long commenterId;
    private long replierId;
    private long withdrawnMemberId;
    private long postId;

    @BeforeEach
    void setUp() {
        String suffix = Long.toString(System.nanoTime());

        jdbcTemplate.update(
                "INSERT INTO post_categories (code, name, is_active, sort_order) VALUES (?, ?, 1, 999)",
                "NOTI_" + suffix, "알림 테스트");
        categoryId = lastInsertId();

        postAuthorId = insertMember("noti-author-" + suffix, "글쓴이", "ACTIVE");
        commenterId = insertMember("noti-commenter-" + suffix, "댓글쓴이", "ACTIVE");
        replierId = insertMember("noti-replier-" + suffix, "답글쓴이", "ACTIVE");
        withdrawnMemberId = insertMember("noti-withdrawn-" + suffix, "떠난이", "WITHDRAWN");
        postId = insertPost(postAuthorId);
    }

    @AfterEach
    void tearDown() {
        deleteNotifications();
        jdbcTemplate.update(
                "DELETE FROM comments WHERE post_id = ? AND parent_comment_id IS NOT NULL", postId);
        jdbcTemplate.update("DELETE FROM comments WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", postId);
        jdbcTemplate.update(
                "DELETE FROM members WHERE id IN (?, ?, ?, ?)",
                postAuthorId, commenterId, replierId, withdrawnMemberId);
        jdbcTemplate.update("DELETE FROM post_categories WHERE id = ?", categoryId);
    }

    @Test
    void addComment_notifiesPostAuthorWithActorNickname() {
        communityCommentService.addComment(postId, commentFormOf("댓글입니다."), commenterId);

        long commentId = latestCommentId();
        assertThat(receiversOf("CUSTOMER_COMMENT")).containsExactly(postAuthorId);
        assertThat(actorOf("CUSTOMER_COMMENT", postAuthorId)).isEqualTo(commenterId);
        assertThat(commentIdOf("CUSTOMER_COMMENT", postAuthorId)).isEqualTo(commentId);
        assertThat(postIdOf("CUSTOMER_COMMENT", postAuthorId)).isEqualTo(postId);
        assertThat(contentOf("CUSTOMER_COMMENT", postAuthorId)).startsWith("댓글쓴이님이");
    }

    @Test
    void addComment_byPostAuthor_notifiesNobody() {
        communityCommentService.addComment(postId, commentFormOf("자문자답입니다."), postAuthorId);

        assertThat(countOfAll()).isZero();
    }

    @Test
    void addReply_notifiesParentCommentAuthorOnly() {
        long rootId = addRootComment(commenterId, "뿌리 댓글입니다.");

        communityCommentService.addReply(
                postId, rootId, commentFormOf("답글입니다."), postAuthorId);

        assertThat(receiversOf("CUSTOMER_COMMENT_REPLY")).containsExactly(commenterId);
        assertThat(actorOf("CUSTOMER_COMMENT_REPLY", commenterId)).isEqualTo(postAuthorId);
        assertThat(commentIdOf("CUSTOMER_COMMENT_REPLY", commenterId)).isEqualTo(latestCommentId());
        assertThat(contentOf("CUSTOMER_COMMENT_REPLY", commenterId)).startsWith("글쓴이님이");
    }

    /** 답글은 글 작성자에게 따로 가지 않는다 — 그 사람은 뿌리 댓글에서 이미 받았다. */
    @Test
    void addReply_onAnotherMembersComment_doesNotNotifyPostAuthor() {
        long rootId = addRootComment(commenterId, "뿌리 댓글입니다.");

        communityCommentService.addReply(
                postId, rootId, commentFormOf("답글입니다."), replierId);

        assertThat(countOf("CUSTOMER_COMMENT", postAuthorId)).isZero();
        assertThat(countOfAll()).isEqualTo(1);
    }

    @Test
    void addReply_byParentCommentAuthor_notifiesNobody() {
        long rootId = addRootComment(commenterId, "뿌리 댓글입니다.");
        long before = countOfAll();

        communityCommentService.addReply(
                postId, rootId, commentFormOf("제 댓글에 제가 답합니다."), commenterId);

        assertThat(countOfAll()).isEqualTo(before);
    }

    /** 탈퇴 회원도 받는다. 표기만 가리고 사건은 그 사람에게 실제로 일어났다. */
    @Test
    void addComment_withdrawnPostAuthor_stillReceivesNotification() {
        long withdrawnPostId = insertPost(withdrawnMemberId);

        try {
            communityCommentService.addComment(
                    withdrawnPostId, commentFormOf("댓글입니다."), commenterId);

            assertThat(countOf("CUSTOMER_COMMENT", withdrawnMemberId)).isEqualTo(1);
        } finally {
            jdbcTemplate.update("DELETE FROM comments WHERE post_id = ?", withdrawnPostId);
            jdbcTemplate.update("DELETE FROM posts WHERE id = ?", withdrawnPostId);
        }
    }

    /** 탈퇴한 작성자의 이름은 문구에서도 가려진다 — 화면과 같은 표기 규칙이다. */
    @Test
    void addComment_byWithdrawnMember_masksActorNameInContent() {
        communityCommentService.addComment(postId, commentFormOf("댓글입니다."), withdrawnMemberId);

        assertThat(contentOf("CUSTOMER_COMMENT", postAuthorId)).startsWith("탈퇴한 회원님이");
    }

    @Test
    void sendNewComment_calledAgainForTheSameComment_leavesOneNotification() {
        communityCommentService.addComment(postId, commentFormOf("댓글입니다."), commenterId);

        communityCommentNotificationSender.sendNewComment(
                postId, latestCommentId(), postAuthorId, commenterId);

        assertThat(countOf("CUSTOMER_COMMENT", postAuthorId)).isEqualTo(1);
    }

    @Test
    void addComment_notificationFails_keepsTheCommentAndDoesNotFailTheRequest() {
        doThrow(new IllegalStateException("알림 실패"))
                .when(notificationService)
                .makeNotification(any());

        communityCommentService.addComment(postId, commentFormOf("댓글입니다."), commenterId);

        assertThat(commentRows()).isEqualTo(1);
        assertThat(countOfAll()).isZero();
    }

    /** 커밋 전에 죽으면 없는 댓글의 알림이 남지 않는다. */
    @Test
    void addComment_rolledBack_sendsNoNotification() {
        doAnswer(invocation -> {
            invocation.callRealMethod();

            throw new IllegalStateException("커밋 직전 실패");
        }).when(communityCommentNotificationService)
                .notifyNewComment(anyLong(), anyLong(), anyLong(), anyLong());

        assertThatThrownBy(() -> communityCommentService.addComment(
                postId, commentFormOf("댓글입니다."), commenterId))
                .isInstanceOf(IllegalStateException.class);

        assertThat(commentRows()).isZero();
        assertThat(countOfAll()).isZero();
    }

    @Test
    void deleteComment_sendsNoNotification() {
        long rootId = addRootComment(commenterId, "지울 댓글입니다.");
        long before = countOfAll();

        communityCommentService.deleteComment(postId, rootId, commenterId);

        assertThat(countOfAll()).isEqualTo(before);
    }

    /** 뿌리 댓글의 알림은 지우고 시작한다 — 뒤이은 단언이 답글의 알림만 세게 한다. */
    private long addRootComment(long authorId, String content) {
        communityCommentService.addComment(postId, commentFormOf(content), authorId);
        deleteNotifications();

        return latestCommentId();
    }

    private void deleteNotifications() {
        jdbcTemplate.update(
                "DELETE FROM notifications WHERE receiver_id IN (?, ?, ?, ?)",
                postAuthorId, commenterId, replierId, withdrawnMemberId);
    }

    private CommentForm commentFormOf(String content) {
        CommentForm form = new CommentForm();
        form.setContent(content);

        return form;
    }

    private long latestCommentId() {
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM comments WHERE post_id = ?", Long.class, postId);
    }

    private long commentRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comments WHERE post_id = ?", Long.class, postId);
    }

    private List<Long> receiversOf(String type) {
        return jdbcTemplate.queryForList(
                "SELECT receiver_id FROM notifications WHERE notification_type = ? AND post_id = ?",
                Long.class,
                type,
                postId);
    }

    private long countOf(String type, long receiverId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications"
                        + " WHERE notification_type = ? AND receiver_id = ?",
                Long.class,
                type,
                receiverId);
    }

    private long countOfAll() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE receiver_id IN (?, ?, ?, ?)",
                Long.class,
                postAuthorId, commenterId, replierId, withdrawnMemberId);
    }

    private long actorOf(String type, long receiverId) {
        return columnOf("actor_id", type, receiverId);
    }

    private long commentIdOf(String type, long receiverId) {
        return columnOf("comment_id", type, receiverId);
    }

    private long postIdOf(String type, long receiverId) {
        return columnOf("post_id", type, receiverId);
    }

    private long columnOf(String column, String type, long receiverId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM notifications"
                        + " WHERE notification_type = ? AND receiver_id = ?",
                Long.class,
                type,
                receiverId);
    }

    private String contentOf(String type, long receiverId) {
        return jdbcTemplate.queryForObject(
                "SELECT content FROM notifications"
                        + " WHERE notification_type = ? AND receiver_id = ?",
                String.class,
                type,
                receiverId);
    }

    private long insertMember(String tag, String nickname, String status) {
        String email = tag + "@cakeshop.local";
        jdbcTemplate.update(
                """
                INSERT INTO members (email, password, name, nickname, phone, role, status)
                VALUES (?, 'encoded-password', ?, ?, '010-0000-0000', 'USER', ?)
                """,
                email, nickname, nickname, status);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    private long insertPost(long authorId) {
        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    member_id, category_id, title, content, status, created_at, updated_at
                ) VALUES (?, ?, '제목', '본문', 'PUBLISHED', ?, ?)
                """,
                authorId, categoryId, CREATED_AT, CREATED_AT);

        return lastInsertId();
    }

    private long lastInsertId() {
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
