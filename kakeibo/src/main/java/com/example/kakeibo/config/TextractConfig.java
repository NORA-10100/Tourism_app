package com.example.kakeibo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;

@Configuration
@ConfigurationProperties(prefix = "aws")
public class TextractConfig {

    private String region = "ap-northeast-1";

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    @Bean(destroyMethod = "close")
    public TextractClient textractClient() {
        return TextractClient.builder()
                .region(Region.of(region))
                .build();
    }
}
