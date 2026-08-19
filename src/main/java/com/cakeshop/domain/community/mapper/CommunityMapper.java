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
@Mapper
public interface CommunityMapper {

    List<PostListRow> findPublishedPosts(
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            @Param("sort") PostSort sort,
            @Param("size") int size,
            @Param("offset") int offset
    );

    long countPublishedPosts(
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword
    );

    PostDetailRow findPostById(
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

    List<PostCategoryView> findActiveCategories();

    boolean existsActiveCategory(
            @Param("categoryId") Long categoryId
    );

    int insertPost(Post post);

    int updatePost(PostUpdateCommand command);

    int deletePost(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    PostLockRow lockPost(
            @Param("postId") long postId
    );

    int increaseLikeCount(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );

    int decreaseLikeCount(
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

    boolean existsLike(
            @Param("postId") long postId,
            @Param("memberId") long memberId
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
}
