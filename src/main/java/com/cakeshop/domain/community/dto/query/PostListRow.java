package com.cakeshop.domain.community.dto.query;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-07
 * 기능 : 커뮤니티 목록 조회 결과
 * 설명 : posts 만 읽은 목록 한 줄이다. 작성자는 회원 도메인에서 받아 Service 가 채운다.
 * ******************************
 *
 * <p>작성자 자리를 아예 두지 않는다. 비워 둔 채로 들고 다니면 화면까지 그대로 새어 나가도
 * 컴파일과 테스트가 통과한다. Service의 화면 조립 단계를 거쳐야만 View DTO가 된다.</p>
 */
public record PostListRow(
        Long id,
        Long memberId,
        String categoryName,
        String title,
        long viewCount,
        long likeCount,
        long commentCount,
        LocalDateTime createdAt
) {
}
