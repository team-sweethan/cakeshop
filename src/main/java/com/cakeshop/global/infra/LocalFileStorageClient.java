package com.cakeshop.global.infra;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

// MVP 구현 — 로컬 디스크 저장 (추후 S3 구현체로 교체 가능)
// 실제 파일은 app.file.upload-dir 아래에 저장하고,
// 반환/DB 저장 값은 웹 접근 경로(app.file.url-prefix 접두)를 사용한다.
@Component
public class LocalFileStorageClient implements FileStorageClient {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path baseDir;
    private final String urlPrefix;
    private final Clock clock;

    public LocalFileStorageClient(
            @Value("${app.file.upload-dir}") String uploadDir,
            @Value("${app.file.url-prefix}") String urlPrefix,
            Clock clock) {
        if (!StringUtils.hasText(uploadDir)) {
            throw new IllegalArgumentException("app.file.upload-dir 설정이 비어 있습니다.");
        }
        String prefix = stripTrailingSlash(urlPrefix);
        // 빈 접두어는 delete() 의 경로 판별을 무력화하므로 기동 시점에 막는다.
        if (!StringUtils.hasText(prefix)) {
            throw new IllegalArgumentException("app.file.url-prefix 설정이 비어 있습니다.");
        }
        this.baseDir = Path.of(uploadDir).toAbsolutePath().normalize();
        this.urlPrefix = prefix;
        this.clock = clock;
    }

    /**
     * 저장 경로 규칙: {@code /{도메인}/{yyyyMM}/{uuid}.{ext}}
     *
     * <p>{@code yyyyMM}은 <b>서울 기준</b>의 달이다({@code ClockConfig}). 시계를 주입받는
     * 이유는 {@code LocalDate.now()}가 JVM 기본 시간대를 읽기 때문인데, 이 저장소에는
     * 그 값을 고정하는 설정이 없어 OS에 달려 있다 — 로컬(KST)에서는 맞고 UTC로 뜬 서버
     * 에서만 어긋난다.
     *
     * <p>어긋나도 <b>아무 증상이 없다.</b> 파일은 정상 저장되고 URL도 그대로 살아 있다.
     * 매일 09:00 KST에 폴더가 넘어가서, 월말 자정~09시 업로드분이 전달 폴더에 들어갈
     * 뿐이다. 나중에 "8월 업로드분"을 폴더 단위로 세거나 옮기려는 순간에야 드러난다.
     */
    @Override
    public String store(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("저장할 파일이 비어 있습니다.");
        }
        // null/빈 값이 그대로 이어붙어 "null/202608" 같은 폴더가 생기는 것을 막는다.
        if (!StringUtils.hasText(directory)) {
            throw new IllegalArgumentException("저장 디렉터리가 비어 있습니다.");
        }
        String relativeDir =
                stripTrailingSlash(directory.trim()) + "/" + LocalDate.now(clock).format(MONTH);
        String filename = UUID.randomUUID() + extension(file.getOriginalFilename());
        Path targetDir = baseDir.resolve(relativeDir).normalize();
        // directory 에 '..' 등이 섞여 baseDir 밖으로 나가는 것을 차단한다.
        if (!targetDir.startsWith(baseDir)) {
            throw new IllegalArgumentException("허용되지 않은 저장 경로입니다: " + directory);
        }
        Path target = targetDir.resolve(filename).normalize();
        // 파일명까지 합쳐진 최종 경로도 baseDir 안에 있는지 다시 확인한다.
        if (!target.startsWith(baseDir)) {
            throw new IllegalArgumentException("허용되지 않은 저장 경로입니다: " + directory);
        }
        try {
            Files.createDirectories(targetDir);
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장에 실패했습니다: " + target, e);
        }
        return urlPrefix + "/" + relativeDir + "/" + filename;
    }

    @Override
    public void delete(String path) {
        if (!StringUtils.hasText(path) || !path.startsWith(urlPrefix + "/")) {
            return;
        }
        String relative = path.substring(urlPrefix.length() + 1);
        Path target = baseDir.resolve(relative).normalize();
        // baseDir 밖 경로는 무시한다.
        if (!target.startsWith(baseDir)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 삭제에 실패했습니다: " + target, e);
        }
    }

    private String extension(String originalFilename) {
        String ext = StringUtils.getFilenameExtension(originalFilename);
        // 터키어 로케일 등에서 I -> ı 로 변환되는 것을 막기 위해 Locale.ROOT 를 명시한다.
        return StringUtils.hasText(ext) ? "." + ext.toLowerCase(Locale.ROOT) : "";
    }

    private String stripTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
