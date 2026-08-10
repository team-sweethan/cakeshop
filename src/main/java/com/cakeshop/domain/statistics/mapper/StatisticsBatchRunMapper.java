package com.cakeshop.domain.statistics.mapper;

import com.cakeshop.domain.statistics.entity.StatisticsBatchRun;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StatisticsBatchRunMapper {

    /** DB 기준 현재 시각을 조회한다. */
    LocalDateTime findCurrentDateTime();

    /** RUNNING 실행을 등록하고 생성된 식별자를 전달받는다. */
    int insertRunningBatch(StatisticsBatchRun batchRun);

    /** heartbeat가 1시간 이상 지난 RUNNING 실행을 실패 처리한다. */
    int failExpiredRunningBatch();

    /** 실행 중인 배치의 생존 확인 시각을 갱신한다. */
    int updateHeartbeat(@Param("batchRunId") long batchRunId);

    /** 백필 진행일과 생존 확인 시각을 함께 갱신한다. */
    int updateBackfillProgress(
            @Param("batchRunId") long batchRunId,
            @Param("lastCompletedDate") LocalDate lastCompletedDate
    );

    /** 실행 중인 배치를 성공 상태로 완료한다. */
    int completeSucceeded(@Param("batchRunId") long batchRunId);

    /** 실행 중인 배치를 실패 상태로 완료한다. */
    int completeFailed(@Param("batchRunId") long batchRunId);

    /** 마지막 성공 일별 집계의 변경 탐색 종료 시각을 조회한다. */
    LocalDateTime findLatestSuccessfulDailyWindowEnd();

    /** 마지막 성공 집계가 완료한 대상 종료일을 조회한다. */
    LocalDate findLatestSuccessfulTargetEndDate();

    /** 첫 일별 집계의 변경 탐색 기준이 되는 마지막 성공 백필 시작 시각을 조회한다. */
    LocalDateTime findLatestSuccessfulBackfillStartedAt();

    /** 가장 최근에 저장된 백필 완료 날짜를 조회한다. */
    LocalDate findLatestBackfillCompletedDate();
}
