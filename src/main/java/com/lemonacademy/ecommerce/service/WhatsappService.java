package com.lemonacademy.ecommerce.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class WhatsappService {

    @Value("${zoepact.api.template-url}")
    private String apiUrl;

    @Value("${zoepact.api.token}")
    private String apiToken;

    @Value("${zoepact.phone-number-id}")
    private String phoneNumberId;

    @Value("${zoepact.template-id}")
    private String templateId;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendOtp(String phone, String otp) {

        log.info("Sending to Zoepact - phone: {}, otp: '{}', otp_length: {}", phone, otp, otp.length());
        log.info("API URL: {}", apiUrl);
        log.info("Phone Number ID: {}", phoneNumberId);
        log.info("Template ID: {}", templateId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("apiToken", apiToken);
        body.add("phone_number_id", phoneNumberId);
        body.add("template_id", templateId);
        body.add("templateVariable-code-1", otp); // confirmed exact field name from Zoepact support
        body.add("phone_number", phone);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    request,
                    String.class);

            log.info("WhatsApp OTP sent successfully to {}. Status: {}",
                    phone,
                    response.getStatusCode());

            log.info("Zoepact Response: {}", response.getBody());

            // NOTE: Zoepact can return HTTP 200 even when status:"0" (failure) is in the body.
            // Consider parsing response.getBody() here and throwing if status != "1".

        } catch (Exception ex) {

            log.error("Failed to send WhatsApp OTP to {} : {}", phone, ex.getMessage());

            throw new RuntimeException("Unable to send OTP via WhatsApp.");
        }
    }
}