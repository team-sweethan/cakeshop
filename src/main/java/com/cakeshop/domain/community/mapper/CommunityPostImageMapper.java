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
// 구현 클래스가 없는데도 동작하는 이유:
//   @Mapper 인터페이스를 MyBatis 가 스캔해서 실행 시점에 구현체를 대신 만들어 준다.
//   메서드 이름 = src/main/resources/mapper/community/CommunityPostImageMapper.xml 의 태그 id.
//
// 게시글 하나에 첨부 이미지 여러 장이 딸린다. DB 에는 저장 경로만 들어가고 파일 자체는 디스크에 있다.
@Mapper
public interface CommunityPostImageMapper {

    List<PostImageView> findImagesByPostId(
            @Param("postId") long postId
    );

    // 지우기 전에 대상들의 저장 경로를 먼저 읽어 둔다. 지우고 나면 어떤 파일을 치울지 알 방법이 없다.
    // imageIds 는 List 라 XML 에서 <foreach> 로 펼쳐져 IN (?, ?, ?) 가 된다.
    // memberId 를 함께 거니 남의 이미지 id 를 섞어 보내도 그 항목은 결과에서 빠진다.
    List<PostImageView> findDeletableImages(
            @Param("postId") long postId,
            @Param("memberId") long memberId,
            @Param("imageIds") List<Long> imageIds
    );

    // 장수 상한(예: 5장)을 확인할 때 쓴다. 이미 붙어 있는 수 + 새로 올린 수를 함께 세야 하기 때문이다.
    int countImagesByPostId(
            @Param("postId") long postId
    );

    // 다음에 붙일 이미지의 순서 번호. 이미지가 없으면 첫 번호가 돌아온다.
    int findNextSortOrder(
            @Param("postId") long postId
    );

    int insertImage(PostImage image);

    // 위 findDeletableImages 와 같은 조건으로 실제로 지운다. int 반환값 = 지워진 행 수.
    int deleteImages(
            @Param("postId") long postId,
            @Param("memberId") long memberId,
            @Param("imageIds") List<Long> imageIds
    );
}
