package com.cakeshop.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** s3 프로필에서 AWS SDK 클라이언트를 구성한다. */
@Configuration
@Profile("s3")
public class AwsS3Config {

    // AWS 기본 자격 증명 체인과 지정된 리전으로 S3 클라이언트 생성
    @Bean
    public S3Client s3Client(
            @Value("${aws.region}") String region,
            @Value("${aws.access-key:}") String accessKey,
            @Value("${aws.secret-key:}") String secretKey,
            @Value("${aws.session-token:}") String sessionToken) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider(
                        accessKey,
                        secretKey,
                        sessionToken))
                .build();
    }

    // 로컬 키가 모두 있으면 사용하고, 없으면 배포 환경의 IAM Role 등 기본 인증 체인을 사용
    private AwsCredentialsProvider credentialsProvider(
            String accessKey,
            String secretKey,
            String sessionToken) {
        boolean hasAccessKey = StringUtils.hasText(accessKey);
        boolean hasSecretKey = StringUtils.hasText(secretKey);
        boolean hasSessionToken = StringUtils.hasText(sessionToken);
        if (hasAccessKey != hasSecretKey) {
            throw new IllegalArgumentException(
                    "AWS_ACCESS_KEY_ID와 AWS_SECRET_ACCESS_KEY를 모두 설정해야 합니다.");
        }
        if (hasSessionToken && !hasAccessKey) {
            throw new IllegalArgumentException(
                    "AWS_SESSION_TOKEN은 Access Key와 Secret Key를 함께 설정해야 합니다.");
        }
        if (hasAccessKey) {
            if (hasSessionToken) {
                return StaticCredentialsProvider.create(
                        AwsSessionCredentials.create(
                                accessKey.trim(),
                                secretKey.trim(),
                                sessionToken.trim()));
            }
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey.trim(), secretKey.trim()));
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
