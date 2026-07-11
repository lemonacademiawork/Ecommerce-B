package com.lemonacademy.ecommerce.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QrDetailsResponse {
    private String merchantName;
    private String upiId;
    private String qrImageUrl;
}
