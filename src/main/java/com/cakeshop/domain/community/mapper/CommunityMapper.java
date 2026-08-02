package com.cakeshop.domain.community.mapper;

import java.util.List;

import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommunityMapper {

    /**
     * 노출 중인 게시글 목록을 최신순으로 조회한다.
     *
     * @param categoryId 카테고리 필터. {@code null}이면 전체 카테고리
     * @param size 한 페이지에 조회할 게시글 수
     * @param offset 조회를 시작할 행 위치
     * @return 노출 중인 게시글 목록
     */
    List<PostListView> findPublishedPosts(
            @Param("categoryId") Long categoryId,
            @Param("size") int size,
            @Param("offset") int offset
    );

    /**
     * 노출 중인 게시글의 전체 개수를 조회한다.
     *
     * @param categoryId 카테고리 필터. {@code null}이면 전체 카테고리
     * @return 조건에 맞는 게시글 개수
     */
    long countPublishedPosts(
            @Param("categoryId") Long categoryId
    );

    /**
     * 게시글 상세를 상태와 무관하게 조회한다.
     *
     * <p>노출 여부는 조회 결과의 상태를 보고 Service가 판단한다(DOMAIN.md 4.3).
     * 여기서 상태로 걸러 버리면 작성자에게 차단 사유를 보여줄 수 없다.
     *
     * @param postId 조회할 게시글 식별자
     * @return 게시글 상세, 없으면 {@code null}
     */
    PostDetailView findPostById(
            @Param("postId") long postId
    );

    /**
     * 노출 중인 게시글의 조회수를 1 증가시킨다.
     *
     * @param postId 대상 게시글 식별자
     * @return 갱신된 행 수. 노출 중이 아니면 0
     */
    int increaseViewCount(
            @Param("postId") long postId
    );

    /**
     * 화면의 선택지로 노출할 활성 카테고리를 조회한다.
     *
     * @return 정렬 순서대로 정렬된 활성 카테고리 목록
     */
    List<PostCategoryView> findActiveCategories();
}
