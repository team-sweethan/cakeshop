package com.cakeshop.domain.notification.dto.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.notification.entity.NotificationType;

import org.junit.jupiter.api.Test;

class NotificationResponseTests {

    /** 댓글 알림은 서버가 대상 스레드를 해석하는 GET 경로와 브라우저 앵커를 함께 가진다. */
    @Test
    void getTargetUrl_comment_usesServerRenderedDeepLink() {
        for (NotificationType type : new NotificationType[] {
                NotificationType.CUSTOMER_COMMENT,
                NotificationType.CUSTOMER_COMMENT_REPLY
        }) {
            NotificationResponse response = NotificationResponse.builder()
                    .type(type)
                    .postId(42L)
                    .commentId(314L)
                    .build();

            assertThat(response.getTargetUrl())
                    .isEqualTo("/community/42/comments/314#comment-314");
        }
    }

    /** 후기 알림도 목록이 아니라 그 후기를 연다. 관리자는 상세, 작성자는 자기 후기 자리다. */
    @Test
    void getTargetUrl_review_opensTheReviewItself() {
        NotificationResponse newReview = NotificationResponse.builder()
                .type(NotificationType.NEW_REVIEW)
                .reviewId(77L)
                .build();

        NotificationResponse replied = NotificationResponse.builder()
                .type(NotificationType.CUSTOMER_REVIEW)
                .reviewId(77L)
                .build();

        assertThat(newReview.getTargetUrl()).isEqualTo("/admin/reviews/77");
        assertThat(replied.getTargetUrl()).isEqualTo("/mypage/reviews/77#review-77");
    }
}
