package com.cakeshop.domain.community.dto.view;

import java.util.List;

// 메인 화면처럼 다른 화면 안에 끼워 넣는 "공지 영역" 한 덩어리다.
// NoticeView 여러 개를 List 하나로 감싸기만 한 상자이고, 화면은 이걸 통째로 받아 영역을 그린다.
// 화면 쪽 조건: th:if="${!noticeSection.isEmpty()}" 로 영역 전체를 그릴지 말지 정한다.
public record NoticeSectionView(
        List<NoticeView> notices
) {

    // 보여 줄 공지가 없을 때 null 대신 쓰는 빈 영역이다.
    // List.of()는 요소가 없는 불변 리스트라 화면에서 그대로 반복해도 안전하다.
    public static NoticeSectionView empty() {
        return new NoticeSectionView(List.of());
    }

    public boolean isEmpty() {
        return notices.isEmpty();
    }
}
