package com.cakeshop.domain.community.mapper;

import java.util.List;

import com.cakeshop.domain.community.dto.command.PostUpdateCommand;
import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.query.PostDetailRow;
import com.cakeshop.domain.community.dto.query.PostListRow;
import com.cakeshop.domain.community.dto.query.PostLockRow;
import com.cakeshop.domain.community.dto.view.PostSort;
import com.cakeshop.domain.community.entity.Post;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 데이터 접근
 * 설명 : CommunityMapper 기능에 필요한 조회와 변경을 수행한다.
 * ******************************
 */
// 구현 클래스가 없는데도 동작하는 이유:
//   @Mapper 가 붙은 인터페이스를 MyBatis 가 스캔해서, 실행 시점에 구현체(프록시)를 대신 만들어 준다.
//   메서드 이름은 src/main/resources/mapper/community/CommunityMapper.xml 의 <select id="...">
//   같은 태그의 id 와 1:1로 짝지어지고, 그 안의 SQL 이 실제로 실행된다.
//   @Param("postId"): XML 에서 #{postId} 로 꺼내 쓸 이름을 정해 준다. 인자가 2개 이상이면 필수다.
@Mapper
public interface CommunityMapper {

    // 게시글 조회
    // 못 찾으면 null 이 온다(Optional 이 아니다). null 판단은 Service 가 한다.
    PostDetailRow findPostById(
            @Param("postId") long postId
    );

    // 목록 한 쪽 = size 개, offset 개만큼 건너뛴 뒤부터. 예: 3쪽 10개씩 -> size=10, offset=20
    // categoryId·keyword 는 null 이면 그 조건을 걸지 않는다(XML 의 <if> 가 통째로 빠진다).
    List<PostListRow> findPublishedPosts(
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            @Param("sort") PostSort sort,
            @Param("size") int size,
            @Param("offset") int offset
    );

    // 위 목록과 같은 조건으로 전체 건수만 센다. 전체 쪽 수를 계산하려면 이 값이 필요하다.
    long countPublishedPosts(
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword
    );

    // 수정·삭제 직전에 SELECT ... FOR UPDATE 로 그 행을 잠근다.
    // 반환은 상태 확인에 필요한 최소 칸만 담은 Row 다.
    PostLockRow lockPost(
            @Param("postId") long postId
    );

    // 카테고리
    List<PostCategoryView> findActiveCategories();

    boolean existsActiveCategory(
            @Param("categoryId") Long categoryId
    );

    // 게시글 쓰기
    // Post 엔티티를 통째로 넘기면 @Param 없이도 XML 이 #{title} 처럼 필드 이름으로 꺼내 쓴다.
    // 새로 생긴 id 를 되돌려 받아야 해서 개별 @Param 이 아니라 객체를 넘긴다.
    int insertPost(Post post);

    int updatePost(PostUpdateCommand command);

    // memberId 를 WHERE 에 함께 건다 = 남의 글을 지우는 요청은 0을 돌려받는다.
    // int 반환값 = 실제로 바뀐 행 수. 0이면 "없거나 내 것이 아니다"라는 뜻이다.
    int deletePost(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    // 조회수: 본 기록을 먼저 남기고(recordView), 그게 성공했을 때만 숫자를 올린다.
    // viewerKey = 로그인 회원이면 "M:37", 비로그인이면 "S:<세션ID>"
    int recordView(
            @Param("postId") long postId,
            @Param("viewerKey") String viewerKey
    );

    int increaseViewCount(
            @Param("postId") long postId,
            @Param("viewerKey") String viewerKey
    );

    // 좋아요: 눌린 적 있는지 확인 -> 기록 넣거나 빼기 -> 게시글의 집계 숫자 올리거나 내리기
    boolean existsLike(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    int insertLike(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    int deleteLike(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    int increaseLikeCount(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    int decreaseLikeCount(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    // 신고: 같은 사람이 같은 글을 두 번 신고하지 못하도록 먼저 확인한다.
    boolean existsReport(
            @Param("postId") long postId,
            @Param("reporterId") long reporterId
    );

    int insertReport(
            @Param("postId") long postId,
            @Param("reporterId") long reporterId,
            @Param("reason") String reason
    );
}
