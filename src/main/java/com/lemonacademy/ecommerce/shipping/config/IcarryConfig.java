package com.lemonacademy.ecommerce.shipping.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@Getter
public class IcarryConfig {

    @Value("${icarry.base-url:https://www.icarry.in}")
    private String baseUrl;

    @Value("${icarry.username:}")
    private String username;

    @Value("${icarry.password:}")
    private String password;

    @Value("${icarry.api-key:}")
    private String apiKey;

    @Value("${icarry.callback-url:}")
    private String callbackUrl;

    @Value("${icarry.default-weight:500}")
    private Integer defaultWeight;

    @Value("${icarry.default-dimensions:10x10x10}")
    private String defaultDimensions;

    @Bean(name = "icarryRestClient")
    public RestClient icarryRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
