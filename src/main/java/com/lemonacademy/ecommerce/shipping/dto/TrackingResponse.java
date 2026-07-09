package com.lemonacademy.ecommerce.shipping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackingResponse {
    private String awbNumber;
    private String shipmentId;
    private String status;
    private String courierName;
    private List<TrackingEvent> events;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TrackingEvent {
        private String timestamp;
        private String location;
        private String activity;
    }
}
