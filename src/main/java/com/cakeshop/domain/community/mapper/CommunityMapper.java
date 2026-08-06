package com.cakeshop.domain.community.mapper;

import java.time.LocalDate;
import java.util.List;

import com.cakeshop.domain.community.dto.view.CommentCountView;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.dto.view.PopularPostView;
import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.dto.view.PostLockView;
import com.cakeshop.domain.community.dto.view.PostSort;
import com.cakeshop.domain.community.entity.Comment;
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
@Mapper
public interface CommunityMapper {

    List<PostListView> findPublishedPosts(
            @Param("categoryId") Long categoryId,
            @Param("sort") PostSort sort,
            @Param("size") int size,
            @Param("offset") int offset
    );

    long countPublishedPosts(
            @Param("categoryId") Long categoryId
    );

    PostDetailView findPostById(
            @Param("postId") long postId
    );

    int increaseViewCount(
            @Param("postId") long postId,
            @Param("viewerKey") String viewerKey
    );

    int recordView(
            @Param("postId") long postId,
            @Param("viewerKey") String viewerKey
    );

    long countViews(
            @Param("postId") long postId
    );

    List<PostCategoryView> findActiveCategories();

    boolean existsActiveCategory(
            @Param("categoryId") Long categoryId
    );

    int insertPost(Post post);

    int updatePost(Post post);

    int deletePost(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    List<CommentView> findRecentComments(
            @Param("postId") long postId,
            @Param("limit") int limit
    );

    CommentCountView countComments(
            @Param("postId") long postId
    );

    CommentView findCommentById(
            @Param("commentId") long commentId
    );

    int insertComment(Comment comment);

    int deleteComment(
            @Param("commentId") long commentId,
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    PostLockView lockPost(
            @Param("postId") long postId
    );

    int insertLike(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    int deleteLike(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    int recalculateLikeCount(
            @Param("postId") long postId
    );

    boolean existsLike(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    long countLikes(
            @Param("postId") long postId
    );

    int insertReport(
            @Param("postId") long postId,
            @Param("reporterId") long reporterId,
            @Param("reason") String reason
    );

    boolean existsReport(
            @Param("postId") long postId,
            @Param("reporterId") long reporterId
    );


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
