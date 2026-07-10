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

    public IcarryClient(
            @org.springframework.beans.factory.annotation.Qualifier("icarryRestClient") RestClient restClient,
            IcarryConfig config,
            @Lazy IcarryAuthService authService) {
        this.restClient = restClient;
        this.config = config;
        this.authService = authService;
    }

    /**
     * Executes a POST request to an authenticated iCarry endpoint.
     * Automatically appends the api_token to the URI.
     * Includes execution logging, retry handling (for safe requests), and centralized error handling.
     *
     * <p>Important: Spring RestClient throws RestClientResponseException for 4xx/5xx by default.
     * We intercept those via .onStatus() and re-wrap them as IcarryApiException with a clean message.</p>
     */
    public String post(String path, Object body, boolean isRetryable) {
        String correlationId = UUID.randomUUID().toString();
        Supplier<String> requestExecutor = () -> {
            String token = authService.getApiToken();
            String uri = path + (path.contains("?") ? "&" : "?") + "api_token=" + token;

            log.info("[iCarry API Request] CorrelationID: {}, URL: {}{}, Body: {}",
                    correlationId, config.getBaseUrl(), path, maskSensitiveData(body));

            long startTime = System.currentTimeMillis();
            try {
                RestClient.RequestBodySpec requestSpec = restClient.post().uri(uri);

                if (body instanceof MultiValueMap) {
                    requestSpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body((MultiValueMap<?, ?>) body);
                } else {
                    requestSpec.contentType(MediaType.APPLICATION_JSON).body(body);
                }

                ResponseEntity<String> response = requestSpec
                        .retrieve()
                        // Intercept 4xx / 5xx before RestClient converts them to exceptions with HTML bodies
                        .onStatus(
                                status -> status.is4xxClientError() || status.is5xxServerError(),
                                (req, resp) -> {
                                    int code = resp.getStatusCode().value();
                                    String errorBody;
                                    try {
                                        errorBody = new String(resp.getBody().readAllBytes());
                                    } catch (Exception readEx) {
                                        errorBody = resp.getStatusCode().toString();
                                    }
                                    // Truncate HTML pages to avoid log pollution
                                    String truncated = errorBody.length() > 400
                                            ? errorBody.substring(0, 400) + "...[truncated]"
                                            : errorBody;
                                    log.error("[iCarry HTTP Error] CorrelationID: {}, HTTP {}, Body: {}",
                                            correlationId, code, truncated);
                                    throw new IcarryApiException(
                                            "iCarry API returned HTTP " + code + " for " + path, code);
                                })
                        .toEntity(String.class);

                long duration = System.currentTimeMillis() - startTime;
                log.info("[iCarry API Response] CorrelationID: {}, Status: {}, Time: {}ms, Body: {}",
                        correlationId, response.getStatusCode(), duration, response.getBody());

                return response.getBody();

            } catch (IcarryApiException e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("[iCarry API Error] CorrelationID: {}, Time: {}ms, Message: {}",
                        correlationId, duration, e.getMessage());
                throw e;
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("[iCarry API Error] CorrelationID: {}, Time: {}ms, Message: {}",
                        correlationId, duration, e.getMessage());
                throw new IcarryApiException("Failed to communicate with iCarry: " + e.getMessage(), 500, e);
            }
        };

        if (isRetryable) {
            return executeWithRetry(requestExecutor, 3, 1000);
        } else {
            return requestExecutor.get();
        }
    }

    public String get(String path, boolean isRetryable) {
        String correlationId = UUID.randomUUID().toString();
        Supplier<String> requestExecutor = () -> {
            String token = authService.getApiToken();
            String uri = path + (path.contains("?") ? "&" : "?") + "api_token=" + token;

            log.info("[iCarry API Request] CorrelationID: {}, URL: {}{}", correlationId, config.getBaseUrl(), path);

            long startTime = System.currentTimeMillis();
            try {
                ResponseEntity<String> response = restClient.get()
                        .uri(uri)
                        .retrieve()
                        .onStatus(
                                status -> status.is4xxClientError() || status.is5xxServerError(),
                                (req, resp) -> {
                                    int code = resp.getStatusCode().value();
                                    throw new IcarryApiException("iCarry API returned HTTP " + code + " for " + path, code);
                                })
                        .toEntity(String.class);

                long duration = System.currentTimeMillis() - startTime;
                log.info("[iCarry API Response] CorrelationID: {}, Status: {}, Time: {}ms", correlationId, response.getStatusCode(), duration);
                return response.getBody();
            } catch (Exception e) {
                log.error("[iCarry API Error] CorrelationID: {}, Message: {}", correlationId, e.getMessage());
                if (e instanceof IcarryApiException) throw (IcarryApiException) e;
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
                // On 401, force-refresh the token and retry immediately (no sleep)
                if (e.getStatusCode() == 401 && attempt < maxAttempts) {
                    log.warn("Unauthorized (401) — refreshing iCarry token and retrying. Attempt {}/{}",
                            attempt, maxAttempts);
                    authService.forceRefresh();
                    continue;
                }
                if (attempt >= maxAttempts) {
                    throw e;
                }
            } catch (Exception e) {
                if (attempt >= maxAttempts) {
                    throw new IcarryApiException(
                            "Failed after " + maxAttempts + " attempts: " + e.getMessage(), 500, e);
                }
            }

            try {
                log.info("Retrying iCarry API call in {}ms... Attempt {}/{}", delayMs, attempt, maxAttempts);
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
        return str.replaceAll("(?i)(password|key|token)=[^&,\\}]+", "$1=*****");
    }
}
