package com.cakeshop.domain.community.mapper;

import java.util.List;

import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.entity.Post;

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

    /**
     * 활성 카테고리인지 확인한다.
     *
     * <p>선택지에 없는 카테고리로 글을 넣지 못하게 막는다(DOMAIN.md 6.8). 화면의
     * {@code select}만 믿으면 요청을 직접 만들어 비활성 카테고리로 보낼 수 있다.
     *
     * @param categoryId 확인할 카테고리 식별자
     * @return 활성 카테고리면 {@code true}
     */
    boolean existsActiveCategory(
            @Param("categoryId") Long categoryId
    );

    /**
     * 게시글을 저장하고 생성된 식별자를 {@code post.id}에 채운다.
     *
     * @param post 저장할 게시글. 작성자·카테고리·제목·본문이 채워져 있어야 한다
     * @return 저장된 행 수
     */
    int insertPost(Post post);

    /**
     * 노출 중인 자기 게시글의 제목·본문·카테고리를 수정한다.
     *
     * <p>소유권과 상태 조건을 SQL에도 둔다. 판단과 에러 응답은 Service가 하지만
     * (404/403을 구분해야 한다), 검증과 UPDATE 사이에 상태가 바뀌면 조건 없는 UPDATE는
     * 차단된 글을 고쳐 버린다.
     *
     * @param post 수정할 내용. {@code id}와 {@code memberId}가 대상과 소유권을 정한다
     * @return 갱신된 행 수. 대상이 없거나 조건에 맞지 않으면 0
     */
    int updatePost(Post post);

    /**
     * 노출 중인 자기 게시글을 삭제 상태로 바꾼다.
     *
     * <p>{@code status = 'PUBLISHED'} 조건이 {@code BLOCKED -> DELETED} 금지(DOMAIN.md 4.2)를
     * SQL 쪽에서도 지킨다.
     *
     * @param postId 삭제할 게시글 식별자
     * @param memberId 요청한 회원 식별자
     * @return 갱신된 행 수. 대상이 없거나 조건에 맞지 않으면 0
     */
    int deletePost(
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );
}
