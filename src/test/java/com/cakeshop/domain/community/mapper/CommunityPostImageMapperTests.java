package com.cakeshop.domain.community.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.community.dto.view.PostImageView;
import com.cakeshop.domain.community.entity.PostImage;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

/** 첨부 이미지 Mapper 계약을 실제 MariaDB로 확인한다. */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommunityPostImageMapperTests {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 1, 10, 0);

    @Autowired
    private CommunityPostImageMapper communityPostImageMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long categoryId;
    private long authorId;
    private long otherMemberId;
    private long postId;

    @BeforeEach
    void setUp() {
        String suffix = Long.toString(System.nanoTime());

        categoryId = insertCategory("IMAGE_TEST_" + suffix);
        authorId = insertMember("image-author-" + suffix + "@cakeshop.local", "글쓴이");
        otherMemberId = insertMember("image-other-" + suffix + "@cakeshop.local", "남");
        postId = insertPost(authorId, PostStatus.PUBLISHED);
    }

    @Test
    void findImagesByPostId_ordersBySortOrder() {
        insertImage(postId, "/uploads/community/b.jpg", 1);
        insertImage(postId, "/uploads/community/a.jpg", 0);

        List<PostImageView> images = communityPostImageMapper.findImagesByPostId(postId);

        assertThat(images).extracting(PostImageView::imageUrl)
                .containsExactly("/uploads/community/a.jpg", "/uploads/community/b.jpg");
    }

    @Test
    void findNextSortOrder_withoutImages_startsAtZero() {
        assertThat(communityPostImageMapper.findNextSortOrder(postId)).isZero();
    }

    @Test
    void findNextSortOrder_withImages_continuesFromMax() {
        insertImage(postId, "/uploads/community/a.jpg", 0);
        insertImage(postId, "/uploads/community/b.jpg", 3);

        assertThat(communityPostImageMapper.findNextSortOrder(postId)).isEqualTo(4);
    }

    @Test
    void insertImage_assignsGeneratedId() {
        PostImage image = PostImage.create(postId, "/uploads/community/a.jpg", 0);

        int inserted = communityPostImageMapper.insertImage(image);

        assertThat(inserted).isEqualTo(1);
        assertThat(image.getId()).isNotNull();
    }

    @Test
    void deleteImages_otherMemberAsRequester_deletesNothing() {
        long imageId = insertImage(postId, "/uploads/community/a.jpg", 0);

        int deleted = communityPostImageMapper.deleteImages(
                postId, otherMemberId, List.of(imageId));

        assertThat(deleted).isZero();
        assertThat(countImages(postId)).isEqualTo(1);
    }

    @Test
    void findDeletableImages_otherMemberAsRequester_findsNothing() {
        long imageId = insertImage(postId, "/uploads/community/a.jpg", 0);

        List<PostImageView> deletable = communityPostImageMapper.findDeletableImages(
                postId, otherMemberId, List.of(imageId));

        assertThat(deletable).isEmpty();
    }

    @Test
    void deleteImages_imageOfAnotherPost_deletesNothing() {
        long otherPostId = insertPost(authorId, PostStatus.PUBLISHED);
        long otherImageId = insertImage(otherPostId, "/uploads/community/a.jpg", 0);

        int deleted = communityPostImageMapper.deleteImages(
                postId, authorId, List.of(otherImageId));

        assertThat(deleted).isZero();
        assertThat(countImages(otherPostId)).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0} 글의 첨부는 작성자도 지울 수 없다")
    @CsvSource({"BLOCKED", "DELETED"})
    void deleteImages_postNotPublished_deletesNothing(PostStatus status) {
        long targetPostId = insertPost(authorId, status);
        long imageId = insertImage(targetPostId, "/uploads/community/a.jpg", 0);

        int deleted = communityPostImageMapper.deleteImages(
                targetPostId, authorId, List.of(imageId));

        assertThat(deleted).isZero();
        assertThat(countImages(targetPostId)).isEqualTo(1);
    }

    @Test
    void deleteImages_ownPublishedPost_deletesOnlyRequestedImages() {
        long kept = insertImage(postId, "/uploads/community/keep.jpg", 0);
        long removed = insertImage(postId, "/uploads/community/remove.jpg", 1);

        int deleted = communityPostImageMapper.deleteImages(postId, authorId, List.of(removed));

        assertThat(deleted).isEqualTo(1);
        assertThat(communityPostImageMapper.findImagesByPostId(postId))
                .extracting(PostImageView::id)
                .containsExactly(kept);
    }

    @ParameterizedTest(name = "글이 {0}이 돼도 첨부 행은 남는다")
    @CsvSource({"BLOCKED", "DELETED"})
    void postImages_surviveBlockAndDelete(PostStatus status) {
        insertImage(postId, "/uploads/community/a.jpg", 0);

        jdbcTemplate.update("UPDATE posts SET status = ? WHERE id = ?", status.name(), postId);

        assertThat(countImages(postId)).isEqualTo(1);
        assertThat(communityPostImageMapper.findImagesByPostId(postId)).hasSize(1);
    }

    private long insertCategory(String code) {
        jdbcTemplate.update(
                """
                INSERT INTO post_categories (code, name, is_active, sort_order)
                VALUES (?, ?, 1, 999)
                """,
                code, "첨부 테스트");

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertMember(String email, String nickname) {
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, nickname, phone, role, status, name, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', ?, ?, ?)
                """,
                email, "encoded-password", nickname, "010-0000-0000",
                nickname, BASE_TIME, BASE_TIME);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertPost(long memberId, PostStatus status) {
        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    member_id, category_id, title, content, status, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                memberId, categoryId, "제목", "본문", status.name(), BASE_TIME, BASE_TIME);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertImage(long targetPostId, String imageUrl, int sortOrder) {
        jdbcTemplate.update(
                """
                INSERT INTO post_images (post_id, image_url, sort_order)
                VALUES (?, ?, ?)
                """,
                targetPostId, imageUrl, sortOrder);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private int countImages(long targetPostId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_images WHERE post_id = ?", Integer.class, targetPostId);
    }
}
