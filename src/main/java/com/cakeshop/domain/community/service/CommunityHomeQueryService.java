package com.cakeshop.domain.community.service;

import com.cakeshop.domain.community.dto.view.NoticeSectionView;
import com.cakeshop.domain.community.dto.view.PopularSectionView;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-11
 * 기능 : 메인 화면용 커뮤니티 조회 계약
 * 설명 : 메인에 실을 인기글과 공지를 커뮤니티가 정한 건수만큼 조회한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class CommunityHomeQueryService {

    /*
     * 메인에 실을 건수. 목록 화면의 10건과 다른 값인 것이 의도다 — 메인은 상품이 주인공이라
     * 10건이면 화면 절반이 커뮤니티가 된다.
     *
     * <p>부르는 쪽이 정하게 두지 않는 이유는 화면이 늘 때마다 값이 흩어지기 때문이다.
     * 흩어지면 "왜 여기는 5인가"를 설명할 자리가 사라진다.
     */
    private static final int POPULAR_POST_LIMIT = 5;

    /*
     * 메인에 실을 공지 건수. 목록 상단의 10건과 다른 값인 것이 의도이고, 인기글의 5건과도
     * 다르다 — 공지는 메인의 서비스 안내와 카테고리 사이에서 자리를 덜 차지해야 한다.
     *
     * <p>건수를 부르는 쪽(`home`)이 아니라 커뮤니티가 갖는 이유는 인기글과 같다.
     */
    private static final int NOTICE_LIMIT = 3;

    private final PopularPostReader popularPostReader;

    private final CommunityNoticeService communityNoticeService;

    /*
     * 메인에 실을 인기글을 확정일과 함께 조회한다.
     *
     * <p>목록 화면과 달리 노출 조건이 없다. 메인에는 쪽도 카테고리 필터도 없어서 그 조건이
     * 성립하지 않는다 — 조건을 끌고 오려고 {@code PageRequest(1, ...)}을 지어내면 목록의 페이지
     * 크기가 바뀔 때 메인이 함께 흔들린다.
     */
    @Transactional(readOnly = true)
    public PopularSectionView getPopularSection() {
        return popularPostReader.read(POPULAR_POST_LIMIT);
    }

    /*
     * 메인에 실을 공지를 조회한다.
     *
     * <p>목록 상단의 "1쪽 + 필터 없음"은 가져오지 않는다 — 메인에는 쪽도 필터도 없어서 조건이
     * 성립하지 않는다. 인기글에서 같은 결정을 했고 이유도 같다.
     *
     * <p><b>새 계약 클래스를 만들지 않는다.</b> 소비 도메인이 같으면 계약도 하나다.
     */
    @Transactional(readOnly = true)
    public NoticeSectionView getNoticeSection() {
        return communityNoticeService.readSection(NOTICE_LIMIT);
    }
}
