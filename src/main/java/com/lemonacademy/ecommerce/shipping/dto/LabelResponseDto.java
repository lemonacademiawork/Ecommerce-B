package com.lemonacademy.ecommerce.shipping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelResponseDto {
    private UUID orderId;
    private String orderNumber;
    private String shipmentId;
    private String awbNumber;
    private String courierName;
    private String labelUrl;
    private String downloadUrl;
    private String viewUrl;
    private String shipmentStatus;
}
