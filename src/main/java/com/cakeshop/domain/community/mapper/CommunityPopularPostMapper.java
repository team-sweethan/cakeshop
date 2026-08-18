package com.cakeshop.domain.community.mapper;

import java.time.LocalDate;
import java.util.List;

import com.cakeshop.domain.community.dto.view.PopularPostView;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommunityPopularPostMapper {

    boolean existsBatchRun(
            @Param("rankingDate") LocalDate rankingDate
    );

    int deleteDailyRanking(
            @Param("rankingDate") LocalDate rankingDate
    );

    int insertDailyRanking(
            @Param("rankingDate") LocalDate rankingDate,
            @Param("limit") int limit
    );

    int insertBatchRun(
            @Param("rankingDate") LocalDate rankingDate,
            @Param("postCount") int postCount
    );

    LocalDate findLatestRankingDate();

    List<PopularPostView> findPopularPosts(
            @Param("rankingDate") LocalDate rankingDate,
            @Param("limit") int limit
    );
}
