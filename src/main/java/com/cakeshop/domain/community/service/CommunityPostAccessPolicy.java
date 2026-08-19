package com.cakeshop.domain.community.service;

import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.global.error.BusinessException;

import org.springframework.stereotype.Component;

@Component
class CommunityPostAccessPolicy {

    boolean isAuthor(Long authorId, Long viewerId) {
        return viewerId != null && viewerId.equals(authorId);
    }

    boolean isVisibleTo(PostStatus status, Long authorId, Long viewerId) {
        return switch (status) {
            case PUBLISHED -> true;
            case BLOCKED -> isAuthor(authorId, viewerId);
            case DELETED -> false;
        };
    }

    void requireVisible(PostStatus status, Long authorId, Long viewerId) {
        if (!isVisibleTo(status, authorId, viewerId)) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }
    }

    void requirePublished(PostStatus status) {
        if (status != PostStatus.PUBLISHED) {
            throw new BusinessException(CommunityErrorCode.BLOCKED_POST);
        }
    }

    void requireEditable(PostStatus status, Long authorId, Long editorId) {
        if (!isAuthor(authorId, editorId) || status == PostStatus.DELETED) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        requirePublished(status);
    }
}
