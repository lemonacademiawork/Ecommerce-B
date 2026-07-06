package com.lemonacademy.ecommerce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class UpstashRedisService {

    private final String url;
    private final String token;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public UpstashRedisService(
            @Value("${UPSTASH_REDIS_REST_URL:}") String url,
            @Value("${UPSTASH_REDIS_REST_TOKEN:}") String token,
            ObjectMapper objectMapper
    ) {
        this.url = url;
        this.token = token;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;

        if (url == null || url.isEmpty() || token == null || token.isEmpty()) {
            log.warn("Upstash Redis REST configuration is incomplete. URL: {}, Token present: {}", 
                     url, token != null && !token.isEmpty());
        } else {
            log.info("Upstash Redis REST Service initialized. URL: {}", url);
        }
    }

    @PostConstruct
    public void testConnection() {
        if (url != null && !url.isEmpty() && token != null && !token.isEmpty()) {
            try {
                JsonNode res = execute(Arrays.asList("PING"));
                if (res != null && "PONG".equalsIgnoreCase(res.asText())) {
                    log.info("Redis connection successful");
                } else {
                    log.warn("Redis ping returned unexpected response: {}", res);
                }
            } catch (Exception e) {
                log.error("Redis connection failed on startup ping: {}", e.getMessage());
            }
        }
    }

    private JsonNode execute(List<String> command) {
        if (url == null || url.isEmpty() || token == null || token.isEmpty()) {
            log.error("Redis connection failed: Upstash Redis REST credentials are not configured.");
            throw new RuntimeException("Unable to connect to Redis: credentials missing.");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            HttpEntity<List<String>> entity = new HttpEntity<>(command, headers);
            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            JsonNode root = objectMapper.readTree(responseEntity.getBody());
            if (root.has("error")) {
                String errMsg = root.get("error").asText();
                log.error("Upstash Redis command failed: {}", errMsg);
                throw new RuntimeException("Redis command failed: " + errMsg);
            }

            return root.get("result");
        } catch (Exception e) {
            log.error("Redis connection failed: error during command execution: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to connect to Redis", e);
        }
    }

    public void set(String key, String value, long timeoutInSeconds) {
        execute(Arrays.asList("SET", key, value, "EX", String.valueOf(timeoutInSeconds)));
        log.info("OTP stored: set key '{}' with TTL {}s", key, timeoutInSeconds);
    }

    public String get(String key) {
        JsonNode result = execute(Arrays.asList("GET", key));
        String val = (result == null || result.isNull()) ? null : result.asText();
        if (val == null) {
            log.info("OTP expired or key not found: '{}'", key);
        } else {
            log.info("OTP fetched: get key '{}'", key);
        }
        return val;
    }

    public void delete(String key) {
        execute(Arrays.asList("DEL", key));
        log.info("OTP deleted: delete key '{}'", key);
    }

    public boolean hasKey(String key) {
        JsonNode result = execute(Arrays.asList("EXISTS", key));
        boolean exists = result != null && result.asInt() == 1;
        log.info("OTP checked: key '{}' exists? {}", key, exists);
        return exists;
    }

    public Long increment(String key) {
        JsonNode result = execute(Arrays.asList("INCR", key));
        Long val = result != null ? result.asLong() : null;
        log.info("OTP counter incremented: key '{}' -> {}", key, val);
        return val;
    }

    public void expire(String key, long timeoutInSeconds) {
        execute(Arrays.asList("EXPIRE", key, String.valueOf(timeoutInSeconds)));
        log.info("OTP expiry set: key '{}' set TTL to {}s", key, timeoutInSeconds);
    }
}
