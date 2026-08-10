package com.cakeshop.domain.member.service;

import com.cakeshop.domain.member.dto.view.MemberSummaryView;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberQueryService {

    private final MemberMapper memberMapper;

    public MemberQueryService(MemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    // 다른 도메인에 회원 Entity 대신 필요한 최소 공개 정보만 제공
    @Transactional(readOnly = true)
    public MemberSummaryView getByMemberId(Long memberId) {
        return memberMapper.findSummaryByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.NOT_FOUND));
    }

    // 전체 회원을 한 번에 적재하지 않도록 공개 목록 조회에 페이징 적용
    @Transactional(readOnly = true)
    public PageResult<MemberSummaryView> findAll(PageRequest pageRequest) {
        PageRequest normalizedPageRequest =
                pageRequest == null
                        ? new PageRequest(null, null)
                        : pageRequest;
        long totalElements = memberMapper.countAllMembers();
        List<MemberSummaryView> members =
                totalElements == 0
                        ? List.of()
                        : memberMapper.findAllSummaries(
                                normalizedPageRequest.getSize(),
                                normalizedPageRequest.getOffset());

        return new PageResult<>(members, normalizedPageRequest, totalElements);
    }
}
