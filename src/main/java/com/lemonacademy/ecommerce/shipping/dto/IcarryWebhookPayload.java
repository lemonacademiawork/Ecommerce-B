package com.lemonacademy.ecommerce.shipping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IcarryWebhookPayload {
    private String client_name;
    private String callback_type;
    private String awb;
    private Integer status;
    private String token;
}
