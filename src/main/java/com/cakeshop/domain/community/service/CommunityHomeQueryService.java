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
     * 메인에 실을 공지 건수. 메인이라는 자리를 소유한 공개 계약이 값을 갖는다.
     *
     * <p>건수를 부르는 쪽(`home`)이 아니라 커뮤니티가 갖는 이유는 화면이 늘 때마다 값이
     * 흩어지기 때문이다. 흩어지면 "왜 여기는 3인가"를 설명할 자리가 사라진다.
     */
    private static final int NOTICE_LIMIT = 3;

    private final CommunityNoticeService communityNoticeService;

    /*
     * 메인에 실을 공지를 조회한다.
     *
     * <p><b>새 계약 클래스를 만들지 않는다.</b> 소비 도메인이 같으면 계약도 하나다.
     */
    @Transactional(readOnly = true)
    public NoticeSectionView getNoticeSection() {
        return communityNoticeService.readSection(NOTICE_LIMIT);
    }
}
