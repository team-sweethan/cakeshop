package com.cakeshop.domain.coupon.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.coupon.dto.form.CouponCreateForm;
import com.cakeshop.domain.coupon.dto.form.CouponSearchCondition;
import com.cakeshop.domain.coupon.dto.form.CouponUpdateForm;
import com.cakeshop.domain.coupon.dto.view.CouponView;
import com.cakeshop.domain.coupon.dto.view.CouponDetailView;
import com.cakeshop.domain.coupon.dto.view.CouponDisplayStatus;
import com.cakeshop.domain.coupon.dto.view.CouponIssuedMemberHistoryView;
import com.cakeshop.domain.coupon.dto.view.CouponIssuedMemberView;
import com.cakeshop.domain.coupon.dto.view.CouponIssueCandidateView;
import com.cakeshop.domain.coupon.dto.view.CouponUpdateView;
import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponTargetType;
import com.cakeshop.domain.coupon.entity.CouponStatus;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.mapper.CouponMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.domain.member.dto.view.MemberCouponView;
import com.cakeshop.domain.member.service.MemberCouponQueryService;
import java.util.Set;
import java.util.Map;
import java.util.function.Function;

/** 관리자 쿠폰의 등록·조회·수정 및 발급 상태 전환을 담당한다. */
@Service
public class CouponAdminService {

    private static final DateTimeFormatter BIRTHDAY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    private final CouponMapper couponMapper;
    private final CouponIssueService couponIssueService;
    private final MemberCouponQueryService memberCouponQueryService;
    private final Clock clock;

    public CouponAdminService(CouponMapper couponMapper, CouponIssueService couponIssueService,
                              MemberCouponQueryService memberCouponQueryService, Clock clock) {
        this.couponMapper = couponMapper;
        this.couponIssueService = couponIssueService;
        this.memberCouponQueryService = memberCouponQueryService;
        this.clock = clock;
    }

    /**
     * 등록 Form을 DB 모델로 옮기고, 생성자를 현재 로그인한 관리자로 기록한다.
     * 발급 수량과 초기 상태는 INSERT 문에서 DB 기본값을 사용한다.
     */
    @Transactional
    public void insertCoupon(CouponCreateForm form, Long adminId) {
        Coupon coupon = new Coupon();

        // 화면 입력값과 DB가 정하는 초기 상태·발급 수량을 분리한다.
        applyForm(coupon, form);
        coupon.setCreatedBy(adminId);

        if (couponMapper.insertCoupon(coupon) != 1) {
            throw new BusinessException(
                    CouponErrorCode.CREATE_FAILED
            );
        }
        couponIssueService.issueOnCouponCreated(coupon);

    }

    @Transactional(readOnly = true)
    public PageResult<CouponIssueCandidateView> searchTargetMembers(
            Long couponId, String searchType, String keyword, Integer page
    ) {
        PageRequest request = new PageRequest(page, 5);
        PageResult<MemberCouponView> memberPage = memberCouponQueryService
                .searchActiveMembers(searchType, keyword, request);
        List<Long> memberIds = memberPage.getContent().stream().map(MemberCouponView::memberId).toList();
        Set<Long> issuedMemberIds = memberIds.isEmpty()
                ? Set.of()
                : Set.copyOf(couponMapper.findIssuedMemberIds(couponId, memberIds));
        return new PageResult<>(
                memberPage.getContent().stream()
                        .map(member -> new CouponIssueCandidateView(
                                member.memberId(), member.name(), maskEmail(member.email()), maskPhone(member.phone()),
                                member.birthDate() == null ? null : member.birthDate().format(BIRTHDAY_FORMATTER),
                                issuedMemberIds.contains(member.memberId())))
                        .toList(),
                request,
                memberPage.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public PageResult<CouponIssuedMemberView> getIssuedMembers(Long couponId, Long memberId, Integer page) {
        PageRequest request = new PageRequest(page, 5);
        long totalElements = couponMapper.countIssuedMemberHistories(couponId, memberId);
        List<CouponIssuedMemberHistoryView> histories = totalElements == 0
                ? List.of()
                : couponMapper.findIssuedMemberHistories(couponId, memberId, request.getSize(), request.getOffset());
        Map<Long, MemberCouponView> membersById = memberCouponQueryService.getMembersByIds(
                        histories.stream().map(CouponIssuedMemberHistoryView::memberId).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(MemberCouponView::memberId, Function.identity()));

        return new PageResult<>(
                histories.stream()
                        .map(history -> toIssuedMemberView(history, membersById.get(history.memberId())))
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                request,
                totalElements
        );
    }

    @Transactional
    public void issueSpecificMember(Long couponId, Long memberId) {
        if (!memberCouponQueryService.lockActiveCouponIssuableMember(memberId)) {
            throw new BusinessException(CouponErrorCode.ISSUE_TARGET_UNAVAILABLE);
        }
        Coupon coupon = findCouponForUpdate(couponId);
        if (coupon.getTargetType() != CouponTargetType.SPECIFIC_MEMBERS
                || coupon.getStatus() != CouponStatus.ACTIVE
                || coupon.getStartsAt().isAfter(now())
                || !coupon.getExpiresAt().isAfter(now())) {
            throw new BusinessException(CouponErrorCode.UPDATE_FAILED);
        }
        if (coupon.getTotalQuantity() == null || coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
            throw new BusinessException(CouponErrorCode.ISSUED_QUANTITY_EXCEEDED);
        }
        if (couponMapper.insertMemberCouponIfAbsent(couponId, memberId, false) != 1) {
            // 조회 시점 이후 회원 상태나 발급 이력이 달라졌으면 성공으로 처리하지 않는다.
            throw new BusinessException(CouponErrorCode.ISSUE_TARGET_UNAVAILABLE);
        }
        if (couponMapper.increaseIssuedQuantityIfAvailable(couponId) != 1) {
            throw new BusinessException(CouponErrorCode.UPDATE_FAILED);
        }
    }

    @Transactional
    public void cancelSpecificMemberCoupon(Long couponId, Long memberId) {
        Coupon coupon = findCouponForUpdate(couponId);
        if (coupon.getTargetType() != CouponTargetType.SPECIFIC_MEMBERS) {
            throw new BusinessException(CouponErrorCode.UPDATE_FAILED);
        }
        if (!coupon.getExpiresAt().isAfter(now())) {
            throw new BusinessException(CouponErrorCode.EXPIRED_COUPON);
        }
        if (couponMapper.deleteAvailableMemberCoupon(couponId, memberId) != 1) {
            // 이미 사용됐거나 다른 관리자가 먼저 취소한 이력은 성공으로 응답하지 않는다.
            throw new BusinessException(CouponErrorCode.ISSUE_CANCELLATION_UNAVAILABLE);
        }
        if (couponMapper.decreaseIssuedQuantity(couponId) != 1) {
            throw new BusinessException(CouponErrorCode.UPDATE_FAILED);
        }
    }

    /** 검색 조건을 정규화한 뒤 COUNT와 목록 쿼리에 동일하게 적용한다. */
    @Transactional(readOnly = true)
    public PageResult<CouponView> getCoupons(
            CouponSearchCondition condition,
            PageRequest pageRequest) {

        // 검색 조건이 없으면 전체 조회용 기본 조건을 만들고, 키워드는 한 번만 정규화한다.
        CouponSearchCondition searchCondition =
                condition == null ? new CouponSearchCondition() : condition;

        searchCondition.setKeyword(
                searchCondition.normalizedKeyword()
        );

        // COUNT와 목록 쿼리에 같은 조건을 사용해야 페이지 수와 실제 결과가 일치한다.
        long totalElements = couponMapper.countCoupons(searchCondition);

        List<CouponView> coupons = totalElements == 0
                ? List.of()
                : couponMapper.findCoupons(
                searchCondition,
                pageRequest.getSize(),
                pageRequest.getOffset()
        );

        return new PageResult<>(
                coupons,
                pageRequest,
                totalElements
        );
    }

    /**
     * 수정 화면에 필요한 값을 반환한다.
     * 종료 쿠폰은 수정 대상이 아니며, 시작 시각을 기준으로 전체/제한 수정 범위를 계산한다.
     */
    @Transactional(readOnly = true)
    public CouponUpdateView getUpdateView(Long couponId) {
        Coupon coupon = findCoupon(couponId);

        if (displayStatusOf(coupon) == CouponDisplayStatus.ENDED) {
            throw new BusinessException(CouponErrorCode.CANNOT_EDIT_ENDED_COUPON);
        }

        // 시작 전에는 정책을 자유롭게 바꿀 수 있고, 시작 후에는 발급 조건 변경을 막는다.
        boolean isFullEdit = coupon.getStartsAt().isAfter(now());
        return CouponUpdateView.from(coupon, displayStatusOf(coupon), isFullEdit);
    }

    /** 이미 발급한 수량보다 총 발급 수량을 낮추지 못하도록 검증한 뒤 수정한다. */
    @Transactional
    public void updateCoupon(
            Long couponId,
            CouponUpdateForm form) {

        Coupon coupon = findCoupon(couponId);

        boolean isFullEdit = validateUpdate(coupon, form);

        applyUpdateForm(coupon, form);

        int updated = isFullEdit
                ? couponMapper.updateCouponBeforeStart(coupon)
                : couponMapper.updateCouponAfterStart(coupon);

        if (updated != 1) {
            // 조회와 UPDATE 사이에 상태·시각·발급 수량이 달라졌다면 최신 상태로 다시 판정한다.
            validateUpdate(findCoupon(couponId), form);
            throw new BusinessException(CouponErrorCode.UPDATE_FAILED);
        }
    }

    /** 발급 중인 쿠폰을 중지 상태로 전환한다. */
    @Transactional
    public void deactivateCoupon(Long couponId) {
        Coupon coupon = findCoupon(couponId);

        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            throw new BusinessException(CouponErrorCode.NOT_ACTIVE);
        }

        if (!coupon.getExpiresAt().isAfter(now())) {
            throw new BusinessException(CouponErrorCode.NOT_ACTIVE);
        }

        if (couponMapper.updateStatus(
                couponId,
                CouponStatus.ACTIVE,
                CouponStatus.INACTIVE
        ) != 1) {
            throw new BusinessException(
                    CouponErrorCode.DEACTIVATE_FAILED
            );
        }
    }

    /** 중지 상태이며 아직 만료되지 않은 쿠폰만 발급 상태로 전환한다. */
    @Transactional
    public void activateCoupon(Long couponId) {
        Coupon coupon = findCoupon(couponId);

        if (coupon.getStatus() != CouponStatus.INACTIVE) {
            throw new BusinessException(CouponErrorCode.NOT_INACTIVE);
        }

        // 스케줄러 실행 전이라도 만료 시각이 지났다면 재개를 막는다.
        if (!coupon.getExpiresAt().isAfter(now())) {
            throw new BusinessException(CouponErrorCode.EXPIRED_COUPON);
        }

        if (couponMapper.updateStatus(
                couponId,
                CouponStatus.INACTIVE,
                CouponStatus.ACTIVE
        ) != 1) {
            throw new BusinessException(
                    CouponErrorCode.ACTIVATE_FAILED
            );
        }
    }

    /** 수정·상태 전환 전에 현재 DB 상태를 조회하고, 없으면 도메인 예외로 바꾼다. */
    private Coupon findCoupon(Long couponId) {
        return couponMapper.findCouponById(couponId)
                .orElseThrow(() -> new BusinessException(
                        CouponErrorCode.NOT_FOUND
                ));
    }

    /**
     * 현재 DB 상태를 기준으로 수정 가능 범위를 검증하고, 전체 수정 여부를 반환한다.
     * 반환값은 SQL의 전체 수정/제한 수정 분기를 결정하며, SQL도 같은 조건을 다시 확인한다.
     */
    private boolean validateUpdate(Coupon coupon, CouponUpdateForm form) {
        if (displayStatusOf(coupon) == CouponDisplayStatus.ENDED) {
            throw new BusinessException(CouponErrorCode.CANNOT_EDIT_ENDED_COUPON);
        }

        if (coupon.getTargetType() == CouponTargetType.SPECIFIC_MEMBERS
                && (form.getTotalQuantity() == null || form.getTotalQuantity() < coupon.getIssuedQuantity())) {
            throw new BusinessException(CouponErrorCode.QUANTITY_BELOW_ISSUED);
        }

        boolean isFullEdit = coupon.getStartsAt().isAfter(now());
        if (isFullEdit) {
            return true;
        }

        if (coupon.getDiscountType() != form.getDiscountType()
                || !sameAmount(coupon.getDiscountValue(), form.getDiscountValue())
                || !sameAmount(coupon.getMinimumOrderAmount(), form.getMinimumOrderAmount())
                || !sameAmount(coupon.getMaximumDiscountAmount(), form.getMaximumDiscountAmount())
                // datetime-local Form은 분 단위까지만 전송하므로 양쪽의 초·나노초를 비교에서 제외한다.
                || !coupon.getStartsAt().truncatedTo(ChronoUnit.MINUTES)
                .equals(form.getStartsAt().truncatedTo(ChronoUnit.MINUTES))) {
            throw new BusinessException(CouponErrorCode.CANNOT_MODIFY_FIELDS);
        }

        // 종료 일시는 기존 종료 일시보다 이전이면 안 되며, 동일 시각은 허용한다.
        if (form.getExpiresAt() == null || coupon.getExpiresAt() == null
                || form.getExpiresAt().isBefore(
                        coupon.getExpiresAt().truncatedTo(ChronoUnit.MINUTES)
                )) {
            throw new BusinessException(CouponErrorCode.EXPIRES_AT_EXTENSION_ONLY);
        }

        return false;
    }

    /** 두 할인 금액이 모두 null인 경우도 같은 값으로 취급한다. */
    private boolean sameAmount(java.math.BigDecimal left, java.math.BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    /** 등록 Form의 발급 대상 정책과 입력값을 Entity에 복사한다. */
    private void applyForm(
            Coupon coupon,
            CouponCreateForm form) {

        coupon.setName(form.getName().trim());
        coupon.setDiscountType(form.getDiscountType());
        coupon.setDiscountValue(form.getDiscountValue());
        coupon.setMinimumOrderAmount(form.getMinimumOrderAmount());

        // 최대 할인 금액은 비율 할인 상한에만 사용한다. 금액 할인 요청값은 저장하지 않는다.
        coupon.setMaximumDiscountAmount(
                maximumDiscountAmountOf(form.getDiscountType(), form.getMaximumDiscountAmount())
        );

        coupon.setTotalQuantity(form.getTargetType() == CouponTargetType.SPECIFIC_MEMBERS
                ? Math.toIntExact(form.getTotalQuantity())
                : null);

        // 화면 정책이 분 단위이므로 저장값도 분 단위로 통일한다.
        coupon.setStartsAt(form.getStartsAt().truncatedTo(ChronoUnit.MINUTES));
        coupon.setExpiresAt(form.getExpiresAt().truncatedTo(ChronoUnit.MINUTES));
        coupon.setTargetType(form.getTargetType());
    }

    /** 수정 Form에는 발급 대상이 없으므로 기존 쿠폰의 정책을 유지한 채 수정 가능 값만 복사한다. */
    private void applyUpdateForm(Coupon coupon, CouponUpdateForm form) {
        coupon.setName(form.getName().trim());
        coupon.setDiscountType(form.getDiscountType());
        coupon.setDiscountValue(form.getDiscountValue());
        coupon.setMinimumOrderAmount(form.getMinimumOrderAmount());
        coupon.setMaximumDiscountAmount(
                maximumDiscountAmountOf(form.getDiscountType(), form.getMaximumDiscountAmount())
        );
        coupon.setTotalQuantity(coupon.getTargetType() == CouponTargetType.SPECIFIC_MEMBERS
                ? Math.toIntExact(form.getTotalQuantity())
                : null);
        coupon.setStartsAt(form.getStartsAt().truncatedTo(ChronoUnit.MINUTES));
        coupon.setExpiresAt(form.getExpiresAt().truncatedTo(ChronoUnit.MINUTES));
    }

    /** 최대 할인 금액은 비율 할인에만 의미가 있으므로 정액 할인에서는 저장값을 비운다. */
    private BigDecimal maximumDiscountAmountOf(
            DiscountType discountType,
            BigDecimal maximumDiscountAmount) {
        return discountType == DiscountType.PERCENTAGE ? maximumDiscountAmount : null;
    }

    /** 관리자 화면용 JSON 응답에는 회원 원본 연락처를 포함하지 않는다. */
    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }

        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 8) {
            return "****";
        }
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }

    /** 관리자 화면용 JSON 응답에도 이메일 원문을 남기지 않는다. */
    private String maskEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return null;
        }

        int atIndex = email.indexOf('@');
        String localPart = email.substring(0, atIndex);
        return localPart.substring(0, Math.min(2, localPart.length())) + "***" + email.substring(atIndex);
    }

    /** 쿠폰 발급 이력과 회원 도메인 프로필을 관리자 목록에 필요한 View로 조합한다. */
    private CouponIssuedMemberView toIssuedMemberView(
            CouponIssuedMemberHistoryView history, MemberCouponView member
    ) {
        if (member == null) {
            return null;
        }
        return new CouponIssuedMemberView(
                history.memberId(), member.name(), member.email(), member.phone(),
                member.birthDate() == null ? null : member.birthDate().format(BIRTHDAY_FORMATTER),
                history.status(), history.usedAt()
        );
    }


    private Coupon findCouponForUpdate(Long couponId) {
        return couponMapper.findCouponByIdForUpdate(couponId)
                .orElseThrow(() -> new BusinessException(CouponErrorCode.NOT_FOUND));
    }
    /** 종료 여부와 관계없이 관리자 상세 화면에 표시할 읽기 전용 데이터를 반환한다. */
    @Transactional(readOnly = true)
    public CouponDetailView getCouponDetail(Long couponId) {
        Coupon coupon = findCoupon(couponId);
        return CouponDetailView.from(coupon, displayStatusOf(coupon));
    }

    /** DB 상태와 시간·발급 수량을 조합한 읽기 전용 화면 상태를 계산한다. */
    private CouponDisplayStatus displayStatusOf(Coupon coupon) {
        return CouponDisplayStatus.from(coupon, now());
    }

    /** SQL의 NOW(6)와 같은 Asia/Seoul 기준 시각을 사용한다. */
    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
