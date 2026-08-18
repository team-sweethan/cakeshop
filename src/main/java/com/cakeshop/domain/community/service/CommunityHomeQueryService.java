package com.cakeshop.domain.community.service;

import com.cakeshop.domain.community.dto.view.NoticeSectionView;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-11
 * 기능 : 메인 화면용 커뮤니티 조회 계약
 * 설명 : 메인에 실을 공지를 커뮤니티가 정한 건수만큼 조회한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class CommunityHomeQueryService {

    /*
     * 메인에 실을 공지 건수. 목록 고정 행의 3건과 값은 같지만 소유는 따로다 — 자리마다 건수를
     * 그 자리의 Service가 갖는다.
     *
     * <p>건수를 부르는 쪽(`home`)이 아니라 커뮤니티가 갖는 이유는 화면이 늘 때마다 값이
     * 흩어지기 때문이다. 흩어지면 "왜 여기는 3인가"를 설명할 자리가 사라진다.
     */
    private static final int NOTICE_LIMIT = 3;

    private final CommunityNoticeService communityNoticeService;

    /*
     * 메인에 실을 공지를 조회한다.
     *
     * <p>목록 고정 행의 "1쪽 + 필터 없음"은 가져오지 않는다 — 메인에는 쪽도 필터도 없어서
     * 조건이 성립하지 않는다. 인기글(조각 13)에서 같은 결정을 했고 이유도 같다.
     *
     * <p><b>새 계약 클래스를 만들지 않는다.</b> 소비 도메인이 같으면 계약도 하나다.
     */
    @Transactional(readOnly = true)
    public NoticeSectionView getNoticeSection() {
        return communityNoticeService.readSection(NOTICE_LIMIT);
    }
}
