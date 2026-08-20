package com.cakeshop.domain.community.mapper;

import java.time.LocalDate;
import java.util.List;

import com.cakeshop.domain.community.dto.view.PopularPostView;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 구현 클래스가 없는데도 동작하는 이유:
//   @Mapper 인터페이스를 MyBatis 가 스캔해서 실행 시점에 구현체를 대신 만들어 준다.
//   메서드 이름 = src/main/resources/mapper/community/CommunityPopularPostMapper.xml 의 태그 id.
//
// 테이블이 둘이다.
//   인기글 순위  : 날짜별 상위 게시글 목록          -> *DailyRanking, findPopularPosts
//   배치 실행 기록: "그 날짜는 돌았다"는 표시 한 줄  -> *BatchRun
// 아래는 읽기 -> 쓰기 순으로 적었고, 실제 배치는 존재 확인 -> 삭제 -> 순위 넣기 -> 기록 넣기 순으로 부른다.
@Mapper
public interface CommunityPopularPostMapper {

    // 그 날짜가 이미 확정됐는지. true 면 배치가 아무것도 하지 않고 나간다.
    boolean existsBatchRun(
            @Param("rankingDate") LocalDate rankingDate
    );

    // 확정된 날 중 가장 최신 날짜. 한 번도 돈 적이 없으면 null 이 온다.
    LocalDate findLatestRankingDate();

    List<PopularPostView> findPopularPosts(
            @Param("rankingDate") LocalDate rankingDate,
            @Param("limit") int limit
    );

    // INSERT ... SELECT 다 — 자바가 목록을 만들어 넘기는 게 아니라 DB 안에서 상위 limit 건을 골라 그대로 넣는다.
    // 그래서 int 반환값이 곧 그날 순위에 오른 글 수가 된다.
    int insertDailyRanking(
            @Param("rankingDate") LocalDate rankingDate,
            @Param("limit") int limit
    );

    int insertBatchRun(
            @Param("rankingDate") LocalDate rankingDate,
            @Param("postCount") int postCount
    );

    int deleteDailyRanking(
            @Param("rankingDate") LocalDate rankingDate
    );
}
