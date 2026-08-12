package com.cakeshop.domain.community.entity;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-12
 * 기능 : 커뮤니티 도메인 모델
 * 설명 : Notice 도메인의 상태와 값을 정의한다.
 * ******************************
 */
public class Notice {

    @Setter
    private Long id;

    private final String title;
    private final String content;
    private final LocalDateTime startsAt;
    private final LocalDateTime endsAt;
    private final Long createdBy;

    private Notice(
            Long id,
            String title,
            String content,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Long createdBy) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.createdBy = createdBy;
    }

    public static Notice create(
            String title,
            String content,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Long createdBy) {
        return new Notice(null, title, content, startsAt, endsAt, createdBy);
    }
}
