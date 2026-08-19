package com.cakeshop.domain.community.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 알림 실패가 댓글 저장을 되돌리면 안 된다. 댓글 트랜잭션 안에서 부르면 알림 쪽 예외가 그 트랜잭션을
// rollback-only 로 만들어, 잡아도 댓글이 함께 사라진다. 그래서 커밋 이후로 미루고 실패는 여기서
// 삼킨다 (specs/community-comment.md D4).
@Service
@Slf4j
@RequiredArgsConstructor
public class CommunityCommentNotificationService {

    private final CommunityCommentNotificationSender communityCommentNotificationSender;

    public void notifyNewComment(long postId, Long commentId, long postAuthorId, long actorId) {
        if (skipsKnownReceiver(commentId, postAuthorId, actorId, "댓글")) {
            return;
        }

        afterCommit(
                () -> communityCommentNotificationSender.sendNewComment(
                        postId, commentId, postAuthorId, actorId),
                "댓글",
                commentId);
    }

    /*
     * 답글은 부모 댓글 작성자에게만 간다. 글 작성자는 그 뿌리 댓글에서 이미 받았고, 함께 보내면
     * 자기 글의 한 스레드에서 답글 수만큼 알림이 쌓인다 (specs/community-comment.md D4).
     *
     * 받는 사람이 아니라 부모의 id 를 넘긴다 — 부모를 읽는 것은 알림의 일이고, 답글 트랜잭션
     * 안에서 읽으면 그 조회 하나가 실패할 때 답글이 함께 사라진다.
     */
    public void notifyNewReply(long postId, Long replyId, long parentCommentId, long actorId) {
        if (hasNoId(replyId, "답글", actorId)) {
            return;
        }

        afterCommit(
                () -> communityCommentNotificationSender.sendNewReply(
                        postId, replyId, parentCommentId, actorId),
                "답글",
                replyId);
    }

    /** 받는 사람이 이미 정해진 댓글은 커밋 후 작업을 등록하기 전에 자기 행위를 접는다. */
    private boolean skipsKnownReceiver(
            Long commentId, long receiverId, long actorId, String what) {
        return hasNoId(commentId, what, actorId) || receiverId == actorId;
    }

    /*
     * 생성 키가 돌아오지 않으면 언박싱에서 죽는데, 그 자리는 이미 커밋을 마친 뒤라 댓글은
     * 저장됐는데 화면만 500 이 된다 — 이 클래스가 발송 예외를 삼키는 이유 그대로다.
     */
    private boolean hasNoId(Long commentId, String what, long actorId) {
        if (commentId != null) {
            return false;
        }

        log.warn("{} 알림을 보낼 수 없다 — 생성된 id 가 없다 (actorId: {})", what, actorId);

        return true;
    }

    private void afterCommit(Runnable send, String what, long commentId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch(send, what, commentId);

            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch(send, what, commentId);
            }
        });
    }

    // 여기서 예외가 나가면 커밋은 끝났는데 화면은 500 이 된다. 댓글은 저장됐으므로 알림만 접는다.
    private void dispatch(Runnable send, String what, long commentId) {
        try {
            send.run();
        } catch (RuntimeException e) {
            log.warn("{} 알림 발송 실패 (commentId: {}, 원인: {})",
                    what, commentId, e.getClass().getSimpleName());
        }
    }
}
