package com.lemonacademy.ecommerce.shipping.client;

import com.lemonacademy.ecommerce.shipping.config.IcarryConfig;
import com.lemonacademy.ecommerce.shipping.exception.IcarryApiException;
import com.lemonacademy.ecommerce.shipping.service.IcarryAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.UUID;
import java.util.function.Supplier;

@Component
@Slf4j
public class IcarryClient {

    private final RestClient restClient;
    private final IcarryConfig config;
    private final IcarryAuthService authService;

    public IcarryClient(RestClient restClient, IcarryConfig config, @Lazy IcarryAuthService authService) {
        this.restClient = restClient;
        this.config = config;
        this.authService = authService;
    }

    /**
     * Executes a POST request to an authenticated iCarry endpoint.
     * Automatically appends the api_token to the URI.
     * Includes execution logging, retry handling (for safe requests), and centralized error handling.
     */
    public String post(String path, Object body, boolean isRetryable) {
        String correlationId = UUID.randomUUID().toString();
        Supplier<String> requestExecutor = () -> {
            String token = authService.getApiToken();
            String uri = path + (path.contains("?") ? "&" : "?") + "api_token=" + token;
            
            // Mask API key or passwords in logs
            log.info("[iCarry API Request] CorrelationID: {}, URL: {}{}, Body: {}", 
                     correlationId, config.getBaseUrl(), path, maskSensitiveData(body));
            
            long startTime = System.currentTimeMillis();
            try {
                RestClient.RequestBodySpec requestSpec = restClient.post()
                        .uri(uri);

                if (body instanceof MultiValueMap) {
                    log.info("[DEBUG] Outbound Form Map: {}", body);
                    requestSpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)
                               .body((MultiValueMap<?, ?>) body);
                } else {
                    requestSpec.contentType(MediaType.APPLICATION_JSON)
                               .body(body);
                }

                ResponseEntity<String> response = requestSpec.retrieve()
                        .toEntity(String.class);

                long duration = System.currentTimeMillis() - startTime;
                log.info("[iCarry API Response] CorrelationID: {}, Status: {}, Time: {}ms, Body: {}", 
                         correlationId, response.getStatusCode(), duration, response.getBody());

                if (response.getStatusCode().isError()) {
                    throw new IcarryApiException("API returned error status: " + response.getStatusCode(), 
                            response.getStatusCode().value());
                }

                return response.getBody();
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("[iCarry API Error] CorrelationID: {}, Time: {}ms, Message: {}", 
                          correlationId, duration, e.getMessage());
                
                if (e instanceof IcarryApiException) {
                    throw (IcarryApiException) e;
                }
                
                // Map connection failures, timeouts, host exceptions
                throw new IcarryApiException("Failed to communicate with iCarry: " + e.getMessage(), 500, e);
            }
        };

        if (isRetryable) {
            return executeWithRetry(requestExecutor, 3, 1000);
        } else {
            return requestExecutor.get();
        }
    }

    private String executeWithRetry(Supplier<String> action, int maxAttempts, long delayMs) {
        int attempt = 0;
        while (attempt < maxAttempts) {
            attempt++;
            try {
                return action.get();
            } catch (IcarryApiException e) {
                // If unauthorized error, try refreshing the token and retry immediately
                if (e.getStatusCode() == 401 && attempt < maxAttempts) {
                    log.warn("Unauthorized error (401) during API call. Force refreshing token and retrying... Attempt {}/{}", attempt, maxAttempts);
                    authService.forceRefresh();
                    continue;
                }
                
                if (attempt >= maxAttempts) {
                    throw e;
                }
            } catch (Exception e) {
                if (attempt >= maxAttempts) {
                    throw new IcarryApiException("Failed after " + maxAttempts + " attempts: " + e.getMessage(), 500, e);
                }
            }
            
            try {
                log.info("Retrying API call in {}ms... Attempt {}/{}", delayMs, attempt, maxAttempts);
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IcarryApiException("Execution interrupted during retry delay", 500, ie);
            }
        }
        throw new IcarryApiException("Failed to execute request due to unexpected state", 500);
    }

    private Object maskSensitiveData(Object body) {
        if (body == null) return null;
        String str = body.toString();
        // Replace potential passwords or tokens in logging
        return str.replaceAll("(?i)(password|key|token)=[^&,\\}]+", "$1=*****");
    }
}
