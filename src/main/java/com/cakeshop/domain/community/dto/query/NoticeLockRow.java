package com.cakeshop.domain.community.dto.query;

import com.cakeshop.domain.community.entity.NoticeStatus;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-12
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : 잠근 공지의 현재 상태를 전달한다.
 * ******************************
 */
public record NoticeLockRow(
        Long id,
        NoticeStatus status
) {
}
