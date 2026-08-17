package com.cakeshop.domain.dashboard.mapper;

import com.cakeshop.domain.dashboard.dto.view.LowStockProductView;
import com.cakeshop.domain.dashboard.dto.view.RecentOrderView;
import com.cakeshop.domain.dashboard.dto.view.TodayPickupScheduleView;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DashboardReadModelMapper {

    /** 결제 승인 시각을 기준으로 현재 유효한 오늘 주문 수를 조회한다. */
    long countTodayOrders(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /** 결제 승인 시각을 기준으로 현재 유효한 오늘 매출 합계를 조회한다. */
    BigDecimal sumTodaySales(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /** 현재 승인 대기 상태인 전체 주문제작 건수를 조회한다. */
    long countApprovalPendingCustomOrders();

    /** 현재 관리자의 확인이 필요한 전체 결제 건수를 조회한다. */
    long countPaymentsRequiringAttention();

    /** 현재 제작 중 상태인 전체 주문제작 건수를 조회한다. */
    long countCustomOrdersInProduction();

    /** 지정한 날짜 범위에 픽업 대기 중인 전체 주문 건수를 조회한다. */
    long countTodayPickups(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /** 지정한 날짜 범위의 픽업 대기 주문을 예정 시각 순서로 조회한다. */
    List<TodayPickupScheduleView> findTodayPickupSchedules(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("queryReferenceAt") LocalDateTime queryReferenceAt,
            @Param("limit") int limit
    );

    /** 관리자 주문 목록과 같은 정렬 기준으로 최근 주문을 조회한다. */
    List<RecentOrderView> findRecentOrders(@Param("limit") int limit);

    /** 판매 중인 일반 상품 가운데 재고가 2개 이하인 전체 상품 수를 조회한다. */
    long countLowStockProducts();

    /** 판매 중인 일반 상품 가운데 재고가 2개 이하인 상품을 재고 순서로 조회한다. */
    List<LowStockProductView> findLowStockProducts(@Param("limit") int limit);

    /** 미처리 신고가 하나 이상 있는 고유 게시글 수를 조회한다. */
    long countPendingReportedPosts();
}
