package com.lemonacademy.ecommerce.shipping.webhook;

import com.lemonacademy.ecommerce.shipping.dto.IcarryWebhookPayload;
import com.lemonacademy.ecommerce.shipping.service.IcarryWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "iCarry Webhook API", description = "Endpoints for handling callbacks from the iCarry platform.")
@Slf4j
public class IcarryWebhookController {

    private final IcarryWebhookService webhookService;

    @PostMapping(value = {
            "/api/webhooks/icarry",
            "/api/webhooks/icarry/shipment-status",
            "/api/webhooks/icarry/ndr",
            "/api/webhooks/icarry/weight-dispute"
    })
    @Operation(summary = "Receive shipment updates", description = "Callback endpoint invoked by iCarry to synchronize shipment statuses, NDR alerts, or weight disputes to order records.")
    public ResponseEntity<Void> handleWebhook(
            @Parameter(description = "The client name identifier (always icarry)") @RequestParam(value = "client_name", required = false) String clientName,
            @Parameter(description = "The callback event type") @RequestParam(value = "callback_type", required = false) String callbackType,
            @Parameter(description = "The AWB tracking reference") @RequestParam(value = "awb", required = false) String awb,
            @Parameter(description = "The status integer mapping code") @RequestParam(value = "status", required = false) Integer status,
            @Parameter(description = "The secure authorization key") @RequestParam(value = "token", required = false) String token,
            @RequestBody(required = false) IcarryWebhookPayload bodyPayload) {

        log.info("Incoming iCarry webhook callback received");

        IcarryWebhookPayload payload;
        if (bodyPayload != null && bodyPayload.getAwb() != null) {
            payload = bodyPayload;
        } else {
            payload = IcarryWebhookPayload.builder()
                    .client_name(clientName)
                    .callback_type(callbackType)
                    .awb(awb)
                    .status(status)
                    .token(token)
                    .build();
        }

        if (payload.getAwb() == null) {
            log.warn("Webhook callback received with no AWB number. Skipping processing.");
            return ResponseEntity.badRequest().build();
        }

        try {
            webhookService.processWebhook(payload);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Webhook processing failed: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }
}
