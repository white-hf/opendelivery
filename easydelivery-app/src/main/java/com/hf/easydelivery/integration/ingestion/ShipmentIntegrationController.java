package com.hf.easydelivery.integration.ingestion;

import com.hf.easydelivery.common.response.AppResponse;
import com.hf.easydelivery.config.ApiKeyVerifier;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;

@RestController
@Profile("!memory")
@RequestMapping("/integration/v1/partners")
public class ShipmentIntegrationController {
    private final ApiKeyVerifier keys;
    private final ShipmentIngestionService service;

    public ShipmentIntegrationController(ApiKeyVerifier keys, ShipmentIngestionService service) {
        this.keys = keys;
        this.service = service;
    }

    @PostMapping("/{partnerCode}/shipments")
    public AppResponse<ShipmentIngestionService.IngestionResult> ingest(
            @RequestHeader(value = "X-Upstream-Api-Key", required = false) String apiKey,
            @PathVariable String partnerCode,
            @Valid @RequestBody CanonicalShipmentRequest request) {
        keys.requireUpstream(apiKey);
        return AppResponse.success("Shipment accepted", service.ingest(partnerCode, request));
    }
}
