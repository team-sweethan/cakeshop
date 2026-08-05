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

    // 회원 PK로 회원 요약 정보 조회
    @Transactional(readOnly = true)
    public MemberSummaryView findByMemberId(Long memberId) {
        return memberMapper.findSummaryByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.NOT_FOUND));
    }

    // 전체 회원 요약 정보 조회
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
