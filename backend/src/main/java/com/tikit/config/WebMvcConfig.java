package com.tikit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * 업로드된 포스터 파일을 공개 주소로 내려주기 위한 설정.
 * 이 설정이 없으면 파일은 저장되지만 브라우저에서 불러올 수 없다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String posterDir;
    private final String posterPublicPrefix;

    public WebMvcConfig(
            @Value("${tikit.upload.poster-dir}") String posterDir,
            @Value("${tikit.upload.poster-public-prefix}") String posterPublicPrefix) {
        this.posterDir = posterDir;
        this.posterPublicPrefix = posterPublicPrefix;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // addResourceLocations는 file:/로 시작하는 절대 경로를 요구한다.
        String location = Path.of(posterDir).toAbsolutePath().normalize().toUri().toString();

        registry.addResourceHandler(posterPublicPrefix + "/**")
                .addResourceLocations(location);
    }
}