package com.lemonacademy.ecommerce.shipping.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.service.UpstashRedisService;
import com.lemonacademy.ecommerce.shipping.config.IcarryConfig;
import com.lemonacademy.ecommerce.shipping.exception.IcarryApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class IcarryAuthService {

    private final RestClient restClient;
    private final IcarryConfig config;
    private final UpstashRedisService redisService;
    private final ObjectMapper objectMapper;
    
    // In-memory token cache fallback
    private final ConcurrentHashMap<String, String> localCache = new ConcurrentHashMap<>();
    private static final String TOKEN_KEY = "icarry_api_token";
    private static final String LOCAL_TOKEN_KEY = "token";

    public IcarryAuthService(@org.springframework.beans.factory.annotation.Qualifier("icarryRestClient") RestClient restClient, IcarryConfig config, 
                             UpstashRedisService redisService, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.config = config;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
    }

    public synchronized String getApiToken() {
        // 1. Try to get token from Redis cache
        String token = null;
        try {
            if (redisService != null) {
                token = redisService.get(TOKEN_KEY);
            }
        } catch (Exception e) {
            log.warn("Redis is unavailable: {}. Falling back to in-memory cache.", e.getMessage());
        }

        // 2. If not found, check in-memory cache
        if (token == null || token.isEmpty()) {
            token = localCache.get(LOCAL_TOKEN_KEY);
        }

        // 3. If still not found, authenticate with iCarry
        if (token == null || token.isEmpty()) {
            token = login();
        }

        return token;
    }

    public synchronized String forceRefresh() {
        return login();
    }

    private String login() {
        log.info("Authenticating with iCarry API at {}/api_login for user: {}", config.getBaseUrl(), config.getUsername());
        
        String username = config.getUsername() != null ? config.getUsername().trim() : null;
        String apiKey = config.getApiKey() != null ? config.getApiKey().trim() : null;
        log.info("DEBUG Auth - Username: '{}', Key Length: {}, Key Starts With: {}", 
                 username, 
                 (apiKey != null ? apiKey.length() : 0), 
                 (apiKey != null && apiKey.length() > 3 ? apiKey.substring(0, 3) + "..." : "null/short"));

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("username", username);
        map.add("key", apiKey); // iCarry uses username and key

        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restClient.post()
                    .uri("/api_login")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(map)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            (req, resp) -> {
                                int code = resp.getStatusCode().value();
                                String body;
                                try {
                                    body = new String(resp.getBody().readAllBytes());
                                } catch (Exception ex) {
                                    body = resp.getStatusCode().toString();
                                }
                                String truncated = body.length() > 300 ? body.substring(0, 300) + "...[truncated]" : body;
                                log.error("iCarry login HTTP error {}: {}", code, truncated);
                                throw new IcarryApiException(
                                        "iCarry authentication returned HTTP " + code, code);
                            })
                    .toEntity(String.class);
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("iCarry login response status: {}, time: {}ms", response.getStatusCode(), duration);

            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("error")) {
                JsonNode errorNode = root.get("error");
                String errorMsg = errorNode.toString();
                log.error("iCarry login failed: {}", errorMsg);
                throw new IcarryApiException("Authentication failed with iCarry: " + errorMsg, 401);
            }

            if (root.has("api_token")) {
                String token = root.get("api_token").asText();
                log.info("Successfully authenticated with iCarry.");
                
                // Cache in Redis for 50 minutes (3000 seconds) - iCarry token is valid for 60 minutes
                try {
                    if (redisService != null) {
                        redisService.set(TOKEN_KEY, token, 3000);
                    }
                } catch (Exception e) {
                    log.warn("Failed to cache token in Redis: {}", e.getMessage());
                }

                // Cache in memory fallback
                localCache.put(LOCAL_TOKEN_KEY, token);
                return token;
            } else {
                throw new IcarryApiException("Invalid response from iCarry auth API: " + response.getBody(), 500);
            }
        } catch (IcarryApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Network or parsing error during iCarry login: {}", e.getMessage(), e);
            throw new IcarryApiException("Network error during iCarry authentication: " + e.getMessage(), 500, e);
        }
    }
}
