package com.cakeshop.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;

class AwsS3ConfigTests {

    private final AwsS3Config config = new AwsS3Config();

    @Test
    void s3Client_localCredentials_usesStaticCredentialsProvider() {
        try (S3Client client = config.s3Client(
                "ap-northeast-2",
                "local-access-key",
                "local-secret-key")) {
            assertThat(client.serviceClientConfiguration().credentialsProvider())
                    .isInstanceOf(StaticCredentialsProvider.class);
            AwsCredentialsProvider credentialsProvider =
                    (AwsCredentialsProvider) client.serviceClientConfiguration()
                            .credentialsProvider();
            assertThat(credentialsProvider.resolveCredentials())
                    .isEqualTo(AwsBasicCredentials.create(
                            "local-access-key",
                            "local-secret-key"));
        }
    }

    @Test
    void s3Client_emptyCredentials_usesDefaultCredentialsProvider() {
        try (S3Client client = config.s3Client("ap-northeast-2", "", "")) {
            assertThat(client.serviceClientConfiguration().credentialsProvider())
                    .isInstanceOf(DefaultCredentialsProvider.class);
        }
    }

    @Test
    void s3Client_partialCredentials_rejectsConfiguration() {
        assertThatThrownBy(() -> config.s3Client(
                "ap-northeast-2",
                "local-access-key",
                ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("모두 설정");
    }
}
