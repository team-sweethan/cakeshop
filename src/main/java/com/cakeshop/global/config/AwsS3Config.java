package com.cakeshop.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** s3 프로필에서 AWS SDK 클라이언트를 구성한다. */
@Configuration
@Profile("s3")
public class AwsS3Config {

    // AWS 기본 자격 증명 체인과 지정된 리전으로 S3 클라이언트 생성
    @Bean
    public S3Client s3Client(@Value("${aws.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }
}
