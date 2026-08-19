package com.cakeshop.domain.community.service;

import java.util.Map;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.community.dto.query.CommentRow;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.mapper.CommunityCommentMapper;
import com.cakeshop.domain.member.dto.view.MemberCommunityView;
import com.cakeshop.domain.notification.dto.form.NotificationRequest;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 댓글 트랜잭션이 커밋된 뒤에 불린다. REQUIRES_NEW 가 없으면 완료 중인 트랜잭션에 얹혀 아무도
// 커밋하지 않고 사라진다. 후기의 ReviewNotificationSender 가 같은 이유로 같은 모양이다
// (specs/community-comment.md D4).
//
// targetUrl 은 채우지 않는다. notifications.target_url 은 알림 migration 이 걷어냈고 남은
// NotificationRequest.targetUrl 은 저장되지 않는다. 링크는 NotificationResponse 가 postId·commentId
// 로 되돌려 준다 (D4 의 남은 위험).
@Service
@RequiredArgsConstructor
public class CommunityCommentNotificationSender {

    private final NotificationService notificationService;
    private final CommunityMemberViewLoader communityMemberViewLoader;
    private final CommunityCommentMapper communityCommentMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendNewComment(long postId, long commentId, long receiverId, long actorId) {
        send(NotificationType.CUSTOMER_COMMENT, postId, commentId, receiverId, actorId);
    }

    /*
     * 받는 사람을 여기서 읽는다. 답글 트랜잭션 안에서 읽으면 알림 때문에 하는 조회 하나가
     * 답글을 되돌릴 수 있어서다 — 이 클래스는 커밋 뒤에 돌고 실패해도 알림만 접힌다.
     *
     * 삽입이 성공한 뒤라 부모가 있었다는 것은 이미 정해져 있고, 삭제는 soft delete 라 행이
     * 남는다. 그래도 없으면 보낼 곳이 없으므로 접는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendNewReply(long postId, long replyId, long parentCommentId, long actorId) {
        CommentRow parent = communityCommentMapper.findCommentById(parentCommentId);

        if (parent == null || parent.memberId() == actorId) {
            return;
        }

        send(NotificationType.CUSTOMER_COMMENT_REPLY, postId, replyId, parent.memberId(), actorId);
    }

    private void send(
            NotificationType type, long postId, long commentId, long receiverId, long actorId) {
        notificationService.makeNotification(NotificationRequest.builder()
                .receiverId(receiverId)
                .actorId(actorId)
                .postId(postId)
                .commentId(commentId)
                .type(type)
                .eventKey(eventKey(type, receiverId, commentId))
                .args(new Object[]{actorName(actorId)})
                .build());
    }

    // 화면의 작성자 표기와 같은 규칙이다. 알림 문구에만 실명이 남으면 탈퇴가 표기를 못 가린다.
    private String actorName(long actorId) {
        Map<Long, MemberCommunityView> actors =
                communityMemberViewLoader.findByIds(Stream.of(actorId));

        return CommentView.authorNameOf(actors.get(actorId));
    }

    // 서버가 채워 주는 기본 키와 값이 같지만 명시한다. 기본값은 "가장 세밀한 연관 ID" 를 고르는
    // 우선순위 사슬에서 나오므로, 그 사슬이 바뀌면 같은 사건의 키가 조용히 달라져 uk 가 막던
    // 중복이 되살아난다.
    private static String eventKey(NotificationType type, long receiverId, long commentId) {
        return type.name() + ":" + receiverId + ":" + commentId;
    }
}
