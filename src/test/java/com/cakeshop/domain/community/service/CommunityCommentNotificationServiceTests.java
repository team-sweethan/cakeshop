package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 발송으로 넘길 식별자와 커밋 전 억제 경계를 확인한다. */
class CommunityCommentNotificationServiceTests {

    private static final long POST_ID = 42L;
    private static final long COMMENT_ID = 314L;
    private static final long PARENT_COMMENT_ID = 313L;
    private static final long RECEIVER_ID = 7L;
    private static final long ACTOR_ID = 99L;

    private CommunityCommentNotificationSender sender;
    private CommunityCommentNotificationService service;

    @BeforeEach
    void setUp() {
        sender = mock(CommunityCommentNotificationSender.class);
        service = new CommunityCommentNotificationService(sender);
    }

    /** 트랜잭션 밖에서는 바로 보낸다 — 기다릴 커밋이 없다. */
    @Test
    void notifyNewComment_sendsToPostAuthor() {
        service.notifyNewComment(POST_ID, COMMENT_ID, RECEIVER_ID, ACTOR_ID);

        verify(sender).sendNewComment(POST_ID, COMMENT_ID, RECEIVER_ID, ACTOR_ID);
    }

    @Test
    void notifyNewComment_byPostAuthor_sendsNothing() {
        service.notifyNewComment(POST_ID, COMMENT_ID, ACTOR_ID, ACTOR_ID);

        verify(sender, never()).sendNewComment(anyLong(), anyLong(), anyLong(), anyLong());
    }

    /** 받는 사람이 아니라 부모의 id 를 넘긴다 — 부모를 읽는 것은 발송 쪽 일이다. */
    @Test
    void notifyNewReply_passesParentCommentIdWithoutReadingIt() {
        service.notifyNewReply(POST_ID, COMMENT_ID, PARENT_COMMENT_ID, ACTOR_ID);

        verify(sender).sendNewReply(POST_ID, COMMENT_ID, PARENT_COMMENT_ID, ACTOR_ID);
    }

    /** 생성 키가 없으면 접는다. 언박싱에서 죽으면 저장된 댓글에 500 이 따라붙는다. */
    @Test
    void notifyNewComment_withoutGeneratedId_isFoldedInsteadOfThrowing() {
        assertThatCode(() -> service.notifyNewComment(POST_ID, null, RECEIVER_ID, ACTOR_ID))
                .doesNotThrowAnyException();

        verify(sender, never()).sendNewComment(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void notifyNewReply_withoutGeneratedId_isFoldedInsteadOfThrowing() {
        assertThatCode(() -> service.notifyNewReply(POST_ID, null, PARENT_COMMENT_ID, ACTOR_ID))
                .doesNotThrowAnyException();

        verify(sender, never()).sendNewReply(anyLong(), anyLong(), anyLong(), anyLong());
    }

    /** 발송이 죽어도 호출한 쪽으로 나가지 않는다 — 댓글은 이미 커밋됐다. */
    @Test
    void notifyNewComment_senderFails_doesNotPropagate() {
        doThrow(new IllegalStateException("발송 실패"))
                .when(sender)
                .sendNewComment(anyLong(), anyLong(), anyLong(), anyLong());

        assertThatCode(() -> service.notifyNewComment(POST_ID, COMMENT_ID, RECEIVER_ID, ACTOR_ID))
                .doesNotThrowAnyException();
    }
}
