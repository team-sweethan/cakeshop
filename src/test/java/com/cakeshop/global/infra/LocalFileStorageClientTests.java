package com.cakeshop.global.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalFileStorageClientTests {

    private static final String URL_PREFIX = "/uploads";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /**
     * 서울에서는 9월 1일 00:30이고 UTC에서는 8월 31일 15:30인 순간.
     *
     * <p>달이 갈리는 순간을 일부러 고른다. 아무 시각이나 쓰면 두 시간대가 같은 달을
     * 가리켜서, 시계를 잘못 읽는 구현도 그대로 통과한다.
     */
    private static final Instant MONTH_BOUNDARY = Instant.parse("2026-08-31T15:30:00Z");

    private Path baseDir;
    private LocalFileStorageClient client;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        this.baseDir = tempDir;
        this.client = clientAt(tempDir, MONTH_BOUNDARY);
    }

    private static LocalFileStorageClient clientAt(Path tempDir, Instant now) {
        return new LocalFileStorageClient(
                tempDir.toString(), URL_PREFIX, Clock.fixed(now, SEOUL));
    }

    @Test
    void store_savesFileAndReturnsWebPath() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cake.JPG", "image/jpeg", "hello".getBytes());

        String path = client.store(file, "product");

        // 반환 경로: /uploads/product/{yyyyMM}/{uuid}.jpg (확장자 소문자)
        //
        // 달을 여기서 다시 계산하지 않고 글자 그대로 적는다. 예전에는 검사도
        // LocalDate.now()로 구해서 구현과 같은 시계를 봤는데, 그러면 양쪽이 함께
        // 틀려도 언제나 통과한다 — 지키려던 것을 하나도 안 지키는 모양이었다.
        assertThat(path).startsWith(URL_PREFIX + "/product/202609/");
        assertThat(path).endsWith(".jpg");

        // 실제 파일이 baseDir 아래에 존재해야 한다.
        Path stored = baseDir.resolve(path.substring(URL_PREFIX.length() + 1));
        assertThat(Files.exists(stored)).isTrue();
    }

    @Test
    void delete_removesStoredFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cake.png", "image/png", "bytes".getBytes());
        String path = client.store(file, "review");
        Path stored = baseDir.resolve(path.substring(URL_PREFIX.length() + 1));
        assertThat(Files.exists(stored)).isTrue();

        client.delete(path);

        assertThat(Files.exists(stored)).isFalse();
    }

    @Test
    void store_rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> client.store(empty, "product"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_rejectsPathTraversalDirectory() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cake.jpg", "image/jpeg", "x".getBytes());

        assertThatThrownBy(() -> client.store(file, "../escape"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_rejectsBlankDirectory() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cake.jpg", "image/jpeg", "x".getBytes());

        // null/공백 디렉터리가 "null/{yyyyMM}" 폴더로 새어나가면 안 된다.
        assertThatThrownBy(() -> client.store(file, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.store(file, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsBlankSettings(@TempDir Path tempDir) {
        Clock clock = Clock.fixed(MONTH_BOUNDARY, SEOUL);

        assertThatThrownBy(() -> new LocalFileStorageClient("", URL_PREFIX, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalFileStorageClient(tempDir.toString(), null, clock))
                .isInstanceOf(IllegalArgumentException.class);
        // "/" 는 후행 슬래시 제거 후 빈 문자열이 되어 delete() 판별을 무력화한다.
        assertThatThrownBy(() -> new LocalFileStorageClient(tempDir.toString(), "/", clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 월별 폴더가 <b>서울 달력</b>을 따르는지 본다.
     *
     * <p>{@link #MONTH_BOUNDARY}는 서울로 9월 1일 00:30, UTC로 8월 31일 15:30이다.
     * {@code LocalDate.now()}로 되돌리면 UTC로 뜬 서버에서 {@code 202608}이 되어
     * <b>월말 자정~09시 업로드가 통째로 전달 폴더에 쌓인다.</b>
     *
     * <p>이 어긋남에는 증상이 없다 — 파일은 정상 저장되고 URL도 살아 있다. 폴더 단위로
     * 세거나 옮기려는 순간에야 드러나는데, 그때는 이미 몇 달치가 섞여 있다.
     */
    @Test
    void store_usesSeoulCalendarMonth_notJvmDefaultZone(@TempDir Path tempDir) {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cake.jpg", "image/jpeg", "x".getBytes());

        String path = clientAt(tempDir, MONTH_BOUNDARY).store(file, "product");

        assertThat(path).startsWith(URL_PREFIX + "/product/202609/");
    }

    /** 같은 순간의 UTC 쪽 달(8월)이 절대 나오지 않는지 반대편도 못박는다. */
    @Test
    void store_neverFallsBackToUtcMonth(@TempDir Path tempDir) {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cake.jpg", "image/jpeg", "x".getBytes());

        String path = clientAt(tempDir, MONTH_BOUNDARY).store(file, "product");

        assertThat(path).doesNotContain("/202608/");
    }

    @Test
    void delete_ignoresPathOutsidePrefix() {
        // url-prefix 로 시작하지 않는 경로는 조용히 무시한다 (예외 없음).
        client.delete("/etc/passwd");
        client.delete(null);
    }
}
