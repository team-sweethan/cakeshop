package com.cakeshop.domain.community.dto.command;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-12
 * 기능 : 커뮤니티 데이터 변경 요청
 * 설명 : 공지 수정에 필요한 값만 담아 Mapper 로 전달한다.
 * ******************************
 */
public record NoticeUpdateCommand(
        long noticeId,
        String title,
        String content,
        LocalDateTime startsAt,
        LocalDateTime endsAt
) {
}
