package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import com.cakeshop.domain.community.dto.form.PostForm;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

/** 첨부 저장이 게시글 트랜잭션과 함께 돌아가는지 실제 DB로 확인한다. */
@SpringBootTest
@MariaDbIntegrationTest
class CommunityPostImageIntegrationTests {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 1, 10, 0);

    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    @Autowired
    private CommunityPostService communityPostService;

    @Autowired
    private CommunityPostImageService communityPostImageService;

    @MockitoBean
    private FileStorageClient fileStorageClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long categoryId;
    private long authorId;

    @BeforeEach
    void setUp() {
        String suffix = Long.toString(System.nanoTime());

        jdbcTemplate.update(
                "INSERT INTO post_categories (code, name, is_active, sort_order) VALUES (?, ?, 1, 999)",
                "IMG_" + suffix, "첨부 통합 테스트");
        categoryId = lastInsertId();
        authorId = insertMember("img-author-" + suffix + "@cakeshop.local", "작성자");

        when(fileStorageClient.store(any(), anyString()))
                .thenAnswer(invocation -> "/uploads/community/202608/"
                        + ((MultipartFile) invocation.getArgument(0)).getOriginalFilename());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM post_images WHERE post_id IN (SELECT id FROM posts WHERE member_id = ?)",
                authorId);
        jdbcTemplate.update("DELETE FROM posts WHERE member_id = ?", authorId);
        jdbcTemplate.update("DELETE FROM members WHERE id = ?", authorId);
        jdbcTemplate.update("DELETE FROM post_categories WHERE id = ?", categoryId);
    }

    @Test
    void createPost_withImages_savesRowsInSelectedOrder() {
        long postId = communityPostService.createPost(form(files(2), List.of()), authorId);

        assertThat(imageUrls(postId))
                .containsExactly(
                        "/uploads/community/202608/0.jpg",
                        "/uploads/community/202608/1.jpg");
    }

    @Test
    void createPost_overLimit_rollsBackPostAndStoresNothing() {
        long postsBefore = countPosts();

        assertThatThrownBy(() -> communityPostService.createPost(form(files(6), List.of()), authorId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.IMAGE_LIMIT_EXCEEDED);

        assertThat(countPosts()).isEqualTo(postsBefore);
        verify(fileStorageClient, never()).store(any(), anyString());
    }

    @Test
    void updatePost_deleteAndAdd_keepsRemainingAndAppends() {
        long postId = communityPostService.createPost(form(files(2), List.of()), authorId);
        long firstImageId = communityPostImageService.getImages(postId).get(0).id();

        communityPostService.updatePost(
                postId, form(files(1), List.of(firstImageId)), authorId);

        assertThat(imageUrls(postId))
                .containsExactly(
                        "/uploads/community/202608/1.jpg",
                        "/uploads/community/202608/0.jpg");
    }

    @Test
    void deletePost_keepsAttachedImages() {
        long postId = communityPostService.createPost(form(files(1), List.of()), authorId);

        communityPostService.deletePost(postId, authorId);

        assertThat(postStatus(postId)).isEqualTo(PostStatus.DELETED.name());
        assertThat(imageUrls(postId)).hasSize(1);
    }

    @Test
    void blockedPost_keepsAttachedImages() {
        long postId = communityPostService.createPost(form(files(1), List.of()), authorId);

        jdbcTemplate.update(
                "UPDATE posts SET status = 'BLOCKED', blocked_reason = ? WHERE id = ?",
                "광고성 게시물", postId);

        assertThat(imageUrls(postId)).hasSize(1);
    }

    private PostForm form(List<MultipartFile> images, List<Long> deleteImageIds) {
        PostForm form = new PostForm();

        form.setCategoryId(categoryId);
        form.setTitle("첨부 있는 글");
        form.setContent("본문");
        form.setImages(images);
        form.setDeleteImageIds(deleteImageIds);

        return form;
    }

    private List<MultipartFile> files(int count) {
        return IntStream.range(0, count)
                .<MultipartFile>mapToObj(index -> new MockMultipartFile(
                        "images", index + ".jpg", "image/jpeg", JPEG_HEADER))
                .toList();
    }

    private List<String> imageUrls(long postId) {
        return jdbcTemplate.queryForList(
                "SELECT image_url FROM post_images WHERE post_id = ? ORDER BY sort_order, id",
                String.class,
                postId);
    }

    private String postStatus(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM posts WHERE id = ?", String.class, postId);
    }

    private long countPosts() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE member_id = ?", Long.class, authorId);
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

        return lastInsertId();
    }

    private long lastInsertId() {
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
