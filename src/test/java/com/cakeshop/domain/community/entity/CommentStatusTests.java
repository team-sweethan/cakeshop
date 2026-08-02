package com.cakeshop.domain.community.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommentStatusTests {

    @Test
    void canTransitionTo_publishedComment_allowsDeletion() {
        assertThat(CommentStatus.PUBLISHED.canTransitionTo(CommentStatus.DELETED)).isTrue();
    }

    @Test
    void canTransitionTo_deletedComment_rejectsEveryTransition() {
        assertThat(CommentStatus.DELETED.canTransitionTo(CommentStatus.PUBLISHED)).isFalse();
        assertThat(CommentStatus.DELETED.canTransitionTo(CommentStatus.DELETED)).isFalse();
        assertThat(CommentStatus.DELETED.canTransitionTo(null)).isFalse();
    }

    @Test
    void canTransitionTo_sameStatus_isRejected() {
        assertThat(CommentStatus.PUBLISHED.canTransitionTo(CommentStatus.PUBLISHED)).isFalse();
    }
}
