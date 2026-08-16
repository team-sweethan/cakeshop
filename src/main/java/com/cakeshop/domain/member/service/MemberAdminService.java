package com.cakeshop.domain.member.service;

import java.time.LocalDate;
import java.util.List;

import com.cakeshop.domain.member.dto.form.MemberAdminListType;
import com.cakeshop.domain.member.dto.form.MemberAdminSearchCondition;
import com.cakeshop.domain.member.dto.view.MemberAdminDetailRow;
import com.cakeshop.domain.member.dto.view.MemberAdminDetailView;
import com.cakeshop.domain.member.dto.view.MemberAdminListRow;
import com.cakeshop.domain.member.dto.view.MemberAdminListView;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.entity.MemberStatusAction;
import com.cakeshop.domain.member.entity.MemberStatusHistory;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.domain.member.mapper.MemberStatusHistoryMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberAdminService {

    private static final String EMPTY_DISPLAY_VALUE = "-";

    private final MemberMapper memberMapper;
    private final MemberStatusHistoryMapper memberStatusHistoryMapper;

    public MemberAdminService(
            MemberMapper memberMapper,
            MemberStatusHistoryMapper memberStatusHistoryMapper) {
        this.memberMapper = memberMapper;
        this.memberStatusHistoryMapper = memberStatusHistoryMapper;
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

    @Transactional(readOnly = true)
    public MemberAdminDetailView getMemberDetail(Long memberId) {
        MemberAdminDetailRow row =
                memberMapper.findAdminMemberDetail(memberId)
                .orElseThrow(() ->
                        new BusinessException(MemberErrorCode.NOT_FOUND));

        return new MemberAdminDetailView(
                row.id(),
                row.name(),
                row.nickname(),
                maskEmail(row.email()),
                maskPhone(row.phone()),
                maskBirthDate(row.birthDate()),
                row.role(),
                row.status(),
                row.createdAt(),
                row.updatedAt(),
                row.suspendedAt(),
                row.suspendedReason(),
                row.withdrawnAt(),
                memberStatusHistoryMapper.findByMemberId(memberId));
    }

    @Transactional
    public String suspendMember(
            Long memberId,
            String reason,
            Long processedBy) {
        String normalizedReason = normalizeStatusReason(reason);

        MemberAdminDetailRow member =
                memberMapper.findAdminMemberDetail(memberId)
                        .orElseThrow(() ->
                                new BusinessException(MemberErrorCode.NOT_FOUND));

        if (!"USER".equals(member.role())
                || member.status() != MemberStatus.ACTIVE) {
            throw new BusinessException(MemberErrorCode.INVALID_STATUS_TRANSITION);
        }

        int updatedRows =
                memberMapper.suspendActiveUser(
                        memberId,
                        normalizedReason);

        if (updatedRows != 1) {
            throw new BusinessException(
                    MemberErrorCode.INVALID_STATUS_TRANSITION);
        }

        insertStatusHistory(
                memberId,
                MemberStatusAction.SUSPEND,
                MemberStatus.ACTIVE,
                MemberStatus.SUSPENDED,
                normalizedReason,
                processedBy);
        return member.email();
    }

    @Transactional
    public void activateMember(
            Long memberId,
            String reason,
            Long processedBy) {
        String normalizedReason = normalizeStatusReason(reason);
        MemberAdminDetailRow member =
                memberMapper.findAdminMemberDetail(memberId)
                        .orElseThrow(() ->
                                new BusinessException(MemberErrorCode.NOT_FOUND));

        if (!"USER".equals(member.role())
                || member.status() != MemberStatus.SUSPENDED) {
            throw new BusinessException(
                    MemberErrorCode.INVALID_STATUS_TRANSITION);
        }

        int updatedRows =
                memberMapper.activateSuspendedUser(memberId);

        if (updatedRows != 1) {
            throw new BusinessException(
                    MemberErrorCode.INVALID_STATUS_TRANSITION);
        }

        insertStatusHistory(
                memberId,
                MemberStatusAction.ACTIVATE,
                MemberStatus.SUSPENDED,
                MemberStatus.ACTIVE,
                normalizedReason,
                processedBy);
    }

    private String normalizeStatusReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                    MemberErrorCode.INVALID_STATUS_REASON);
        }

        String normalizedReason = reason.strip();

        if (normalizedReason.length() > 500) {
            throw new BusinessException(
                    MemberErrorCode.INVALID_STATUS_REASON);
        }

        return normalizedReason;
    }

    private void insertStatusHistory(
            Long memberId,
            MemberStatusAction action,
            MemberStatus beforeStatus,
            MemberStatus afterStatus,
            String reason,
            Long processedBy) {
        MemberStatusHistory history = new MemberStatusHistory();

        history.setMemberId(memberId);
        history.setAction(action);
        history.setBeforeStatus(beforeStatus);
        history.setAfterStatus(afterStatus);
        history.setReason(reason);
        history.setProcessedBy(processedBy);

        if (memberStatusHistoryMapper.insert(history) != 1) {
            throw new BusinessException(
                    MemberErrorCode.STATUS_HISTORY_SAVE_FAILED);
        }
    }

    private MemberAdminListView toListView(MemberAdminListRow row) {
        return new MemberAdminListView(
                row.id(),
                row.name(),
                maskEmail(row.email()),
                maskPhone(row.phone()),
                maskBirthDate(row.birthDate()),
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

    private String maskBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            return EMPTY_DISPLAY_VALUE;
        }

        return birthDate.getYear() + ".**.**";
    }
}
