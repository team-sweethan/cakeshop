package com.cakeshop.domain.community.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostStatusTests {

    @Test
    void canTransitionTo_publishedPost_allowsDeletionAndBlocking() {
        assertThat(PostStatus.PUBLISHED.canTransitionTo(PostStatus.DELETED)).isTrue();
        assertThat(PostStatus.PUBLISHED.canTransitionTo(PostStatus.BLOCKED)).isTrue();
    }

    @Test
    void canTransitionTo_blockedPost_allowsOnlyUnblocking() {
        assertThat(PostStatus.BLOCKED.canTransitionTo(PostStatus.PUBLISHED)).isTrue();
    }

    @Test
    void canTransitionTo_blockedPost_rejectsDeletion() {
        // 차단된 글은 신고·조치의 증거다. 작성자가 지워서 없앨 수 없어야 한다.
        assertThat(PostStatus.BLOCKED.canTransitionTo(PostStatus.DELETED)).isFalse();
    }

    @Test
    void canTransitionTo_deletedPost_rejectsEveryTransition() {
        assertThat(PostStatus.DELETED.canTransitionTo(PostStatus.PUBLISHED)).isFalse();
        assertThat(PostStatus.DELETED.canTransitionTo(PostStatus.BLOCKED)).isFalse();
        assertThat(PostStatus.DELETED.canTransitionTo(PostStatus.DELETED)).isFalse();
        assertThat(PostStatus.DELETED.canTransitionTo(null)).isFalse();
    }

    @Test
    void canTransitionTo_sameStatus_isRejected() {
        assertThat(PostStatus.PUBLISHED.canTransitionTo(PostStatus.PUBLISHED)).isFalse();
        assertThat(PostStatus.BLOCKED.canTransitionTo(PostStatus.BLOCKED)).isFalse();
    }

    @Test
    void canTransitionTo_nullTarget_isRejected() {
        assertThat(PostStatus.PUBLISHED.canTransitionTo(null)).isFalse();
        assertThat(PostStatus.BLOCKED.canTransitionTo(null)).isFalse();
    }
}
