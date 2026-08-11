package com.cakeshop.domain.community.service;

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
 * 설명 : 메인에 실을 인기글을 커뮤니티가 정한 건수만큼 확정된 스냅샷에서 조회한다.
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

    private final PopularPostReader popularPostReader;

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
}
