package com.cakeshop.domain.community.mapper;

import java.util.List;

import com.cakeshop.domain.community.dto.view.PostImageView;
import com.cakeshop.domain.community.entity.PostImage;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-18
 * 기능 : 커뮤니티 데이터 접근
 * 설명 : CommunityPostImageMapper 첨부 이미지의 조회와 변경을 수행한다.
 * ******************************
 */
@Mapper
public interface CommunityPostImageMapper {

    List<PostImageView> findImagesByPostId(
            @Param("postId") long postId
    );

    int countImagesByPostId(
            @Param("postId") long postId
    );

    int findNextSortOrder(
            @Param("postId") long postId
    );

    int insertImage(PostImage image);

    /**
     * 지울 이미지의 저장 경로를 삭제 전에 읽는다. 지우고 나면 파일 경로를 알 방법이 없어
     * 커밋 뒤 파일 정리를 걸 수 없다.
     */
    List<PostImageView> findDeletableImages(
            @Param("postId") long postId,
            @Param("memberId") long memberId,
            @Param("imageIds") List<Long> imageIds
    );

    int deleteImages(
            @Param("postId") long postId,
            @Param("memberId") long memberId,
            @Param("imageIds") List<Long> imageIds
    );

}
