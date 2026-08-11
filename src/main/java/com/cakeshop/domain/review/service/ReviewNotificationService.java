package com.cakeshop.domain.review.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 알림 실패가 후기 저장을 되돌리면 안 된다. 후기 트랜잭션 안에서 부르면 알림 쪽 예외가 그 트랜잭션을
// rollback-only 로 만들어, 잡아도 후기가 함께 사라진다. 그래서 커밋 이후로 미루고 실패는 여기서
// 삼킨다 (specs/review-notification.md D2).
@Service
public class ReviewNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ReviewNotificationService.class);

    private final ReviewNotificationSender reviewNotificationSender;

    public ReviewNotificationService(ReviewNotificationSender reviewNotificationSender) {
        this.reviewNotificationSender = reviewNotificationSender;
    }

    public void notifyNewReview(long reviewId, long authorId) {
        afterCommit(
                () -> reviewNotificationSender.sendNewReview(reviewId, authorId),
                "신규 후기",
                reviewId);
    }

    public void notifyReviewReply(
            long reviewId, long reviewReplyId, long authorId, long adminId) {

        afterCommit(
                () -> reviewNotificationSender.sendReviewReply(
                        reviewId, reviewReplyId, authorId, adminId),
                "후기 답글",
                reviewId);
    }

    private void afterCommit(Runnable send, String what, long reviewId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch(send, what, reviewId);

            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch(send, what, reviewId);
            }
        });
    }

    // 여기서 예외가 나가면 커밋은 끝났는데 화면은 500 이 된다. 후기는 저장됐으므로 알림만 접는다.
    private void dispatch(Runnable send, String what, long reviewId) {
        try {
            send.run();
        } catch (RuntimeException e) {
            log.warn("{} 알림 발송 실패 (reviewId: {}, 원인: {})",
                    what, reviewId, e.getClass().getSimpleName());
        }
    }
}
