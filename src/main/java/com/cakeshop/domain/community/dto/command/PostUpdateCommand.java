package com.cakeshop.domain.community.dto.command;

/** Service가 수정 자격과 입력을 검증한 뒤 Mapper에 전달하는 게시글 수정 값이다. */
public record PostUpdateCommand(
        long postId,
        long memberId,
        long categoryId,
        String title,
        String content
) {
}
