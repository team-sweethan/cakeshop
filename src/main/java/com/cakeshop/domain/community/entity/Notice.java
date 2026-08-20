package com.cakeshop.domain.community.entity;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

// @Getter 가 getId(), getTitle(), getContent(), getStartsAt(), getEndsAt(), getCreatedBy() 를 만들어 준다
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

    // @Setter 가 여기에만 붙어서 setId() 하나만 생긴다
    @Setter
    private Long id;

    private final String title;
    private final String content;

    // LocalDateTime: 날짜와 시각을 함께 담는 타입 (예: 2026-08-12T09:00)
    // 시간대(zone) 정보는 들어 있지 않다
    private final LocalDateTime startsAt;
    private final LocalDateTime endsAt;
    private final Long createdBy;

    // 매개변수가 6개라 한 줄에 하나씩 끊어 적었다. 여전히 생성자 하나이고 private 이다
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

    // 생성자와 매개변수가 하나(id) 만 다르다
    // 밖에서는 이 create() 만 보이므로 id 를 직접 넣은 Notice 는 만들 수 없다
    public static Notice create(
            String title,
            String content,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Long createdBy) {
        return new Notice(null, title, content, startsAt, endsAt, createdBy);
    }
}
