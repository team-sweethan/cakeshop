package com.cakeshop.domain.member.service;

import java.util.List;

import com.cakeshop.domain.member.dto.form.MemberAdminListType;
import com.cakeshop.domain.member.dto.form.MemberAdminSearchCondition;
import com.cakeshop.domain.member.dto.view.MemberAdminListRow;
import com.cakeshop.domain.member.dto.view.MemberAdminListView;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberAdminService {

    private static final String EMPTY_DISPLAY_VALUE = "-";

    private final MemberMapper memberMapper;

    public MemberAdminService(MemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    /**
     * 관리자 회원 검색 조건에 맞는 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public PageResult<MemberAdminListView> getMembers(
            MemberAdminSearchCondition condition,
            PageRequest pageRequest) {
        MemberAdminSearchCondition normalizedCondition =
                condition == null
                        ? new MemberAdminSearchCondition()
                        : condition;

        normalizedCondition.setKeyword(
                normalizedCondition.normalizedKeyword());

        if (normalizedCondition.getListType() == null) {
            normalizedCondition.setListType(MemberAdminListType.MEMBERS);
        }

        if (!normalizedCondition.isMembers()
                || normalizedCondition.getStatus() == MemberStatus.WITHDRAWN) {
            normalizedCondition.setStatus(null);
        }

        PageRequest normalizedPageRequest =
                pageRequest == null
                        ? new PageRequest(null, null)
                        : pageRequest;

        long totalElements =
                memberMapper.countAdminMembers(normalizedCondition);

        List<MemberAdminListView> members =
                totalElements == 0
                        ? List.of()
                        : memberMapper.findAdminMembers(
                                        normalizedCondition,
                                        normalizedPageRequest.getSize(),
                                        normalizedPageRequest.getOffset())
                                .stream()
                                .map(this::toListView)
                                .toList();

        return new PageResult<>(
                members,
                normalizedPageRequest,
                totalElements);
    }

    private MemberAdminListView toListView(MemberAdminListRow row) {
        return new MemberAdminListView(
                row.id(),
                row.name(),
                maskEmail(row.email()),
                maskPhone(row.phone()),
                maskBirthDate(row),
                row.status(),
                row.createdAt(),
                row.withdrawnAt());
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return EMPTY_DISPLAY_VALUE;
        }

        int separatorIndex = email.lastIndexOf('@');

        if (separatorIndex <= 0 || separatorIndex == email.length() - 1) {
            return EMPTY_DISPLAY_VALUE;
        }

        String localPart = email.substring(0, separatorIndex);
        String domain = email.substring(separatorIndex + 1);
        int visibleLength =
                Math.min(2, Math.max(0, localPart.length() - 1));

        return localPart.substring(0, visibleLength)
                + "***@"
                + domain;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return EMPTY_DISPLAY_VALUE;
        }

        String digits = phone.replaceAll("\\D", "");

        if (digits.length() < 8) {
            return EMPTY_DISPLAY_VALUE;
        }

        String prefix = digits.substring(0, 3);
        String suffix = digits.substring(digits.length() - 4);

        return prefix + "-****-" + suffix;
    }

    private String maskBirthDate(MemberAdminListRow row) {
        if (row.birthDate() == null) {
            return EMPTY_DISPLAY_VALUE;
        }

        return row.birthDate().getYear() + ".**.**";
    }
}
