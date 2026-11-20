package com.tikit.performance.service;

import com.tikit.common.ErrorCode;
import com.tikit.performance.exception.PerformanceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 공연 포스터·배너 이미지를 서버 디스크에 저장한다.
 * 저장된 파일은 WebMvcConfig가 설정한 공개 주소로 제공된다.
 */
@Service
public class ImageStorageService {

    // 확장자와 Content-Type을 짝으로 둔다. 확장자만 이미지로 바꾼 파일을 거르기 위해 둘 다 확인한다.
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    );

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final Path baseDir;
    private final String publicPrefix;
    private final Clock clock;

    public ImageStorageService(
            @Value("${tikit.upload.image-dir}") String posterDir,
            @Value("${tikit.upload.image-public-prefix}") String publicPrefix,
            Clock clock) {
        this.baseDir = Path.of(posterDir).toAbsolutePath().normalize();
        this.publicPrefix = publicPrefix;
        this.clock = clock;
    }

    /**
     * 파일을 저장하고 공개 주소를 돌려준다.
     * 예: /uploads/images/2026/11/3f2a....jpg
     */
    public String store(MultipartFile file) {
        validate(file);

        String extension = extensionOf(file.getOriginalFilename());
        LocalDate today = LocalDate.now(clock);

        // 한 폴더에 파일이 무한정 쌓이지 않도록 연/월로 나눈다.
        String relativeDir = String.format("%04d/%02d", today.getYear(), today.getMonthValue());

        // 사용자가 보낸 파일명은 쓰지 않는다. ../ 같은 경로 조작을 원천적으로 막기 위함이다.
        String savedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        Path targetDir = baseDir.resolve(relativeDir).normalize();
        Path target = targetDir.resolve(savedName).normalize();

        // 혹시라도 저장 경로가 기준 폴더를 벗어나면 저장하지 않는다.
        if (!target.startsWith(baseDir)) {
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }

        try {
            Files.createDirectories(targetDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new PerformanceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return publicPrefix + "/" + relativeDir + "/" + savedName;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }

        if (file.getSize() > MAX_BYTES) {
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }

        String extension = extensionOf(file.getOriginalFilename());
        String expectedContentType = ALLOWED_TYPES.get(extension);

        if (expectedContentType == null) {
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }

        if (!expectedContentType.equalsIgnoreCase(file.getContentType())) {
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private String extensionOf(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }

        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }

        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
