package com.cakeshop.domain.review.service;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.member.service.MemberReviewQueryService;
import com.cakeshop.domain.notification.dto.form.NotificationRequest;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationService;

// 후기 트랜잭션이 커밋된 뒤에 불린다. REQUIRES_NEW 가 없으면 완료 중인 트랜잭션에 얹혀 아무도
// 커밋하지 않고 사라진다. notification 도메인의 NotificationDeliveryService 가 같은 이유로 같은
// 모양이다 (specs/review-notification.md D2).
//
// targetUrl 은 채우지 않는다. notifications.target_url 은 알림 migration 이 걷어냈고 남은
// NotificationRequest.targetUrl 은 저장되지 않는다. 링크는 NotificationResponse 가 타입별 ID 로
// 되돌려 주며, 후기는 아직 "/mypage" 하나다 (D2 의 남은 위험).
@Service
@RequiredArgsConstructor
public class ReviewNotificationSender {

    private final NotificationService notificationService;
    private final MemberReviewQueryService memberReviewQueryService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendNewReview(long reviewId, long authorId) {
        List<Long> adminIds = memberReviewQueryService.findActiveAdminIds();

        for (Long adminId : adminIds) {
            notificationService.makeNotification(NotificationRequest.builder()
                    .receiverId(adminId)
                    .actorId(authorId)
                    .reviewId(reviewId)
                    .type(NotificationType.NEW_REVIEW)
                    .eventKey(newReviewEventKey(adminId, reviewId))
                    .args(new Object[0])
                    .build());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendReviewReply(long reviewId, long reviewReplyId, long authorId, long adminId) {
        notificationService.makeNotification(NotificationRequest.builder()
                .receiverId(authorId)
                .actorId(adminId)
                .reviewId(reviewId)
                .reviewReplyId(reviewReplyId)
                .type(NotificationType.CUSTOMER_REVIEW)
                .eventKey(reviewReplyEventKey(authorId, reviewReplyId))
                .args(new Object[0])
                .build());
    }

    // 서버가 채워 주는 기본 키와 값이 같지만 명시한다. 기본값은 "가장 세밀한 연관 ID" 를 고르는
    // 우선순위 사슬에서 나오므로, 그 사슬이 바뀌면 같은 사건의 키가 조용히 달라져 uk 가 막던
    // 중복이 되살아난다.
    private static String newReviewEventKey(long adminId, long reviewId) {
        return NotificationType.NEW_REVIEW.name() + ":" + adminId + ":" + reviewId;
    }

    private static String reviewReplyEventKey(long authorId, long reviewReplyId) {
        return NotificationType.CUSTOMER_REVIEW.name() + ":" + authorId + ":" + reviewReplyId;
    }
}
