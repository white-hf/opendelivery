package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.response.AppResponse;
import com.hf.easydelivery.config.ApiKeyVerifier;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;

import java.util.List;

@RestController
@Profile("!memory")
@RequestMapping("/ops/v1")
public class OperationsController {
    private final ApiKeyVerifier keys;
    private final OperationsService service;

    public OperationsController(ApiKeyVerifier keys, OperationsService service) {
        this.keys = keys;
        this.service = service;
    }

    @PostMapping("/manifests/{manifestNo}/receipts")
    public AppResponse<OperationsService.ReceiptResult> receive(
            @RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey,
            @PathVariable String manifestNo,
            @Valid @RequestBody ManifestReceiptRequest request) {
        keys.requireOperations(apiKey);
        return AppResponse.success("Parcel received", service.receive(manifestNo, request));
    }

    @PostMapping("/waves")
    public AppResponse<OperationsService.WaveResult> createWave(
            @RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey,
            @Valid @RequestBody CreateWaveRequest request) {
        keys.requireOperations(apiKey);
        return AppResponse.success("Wave published", service.createAndPublishWave(request));
    }

    @GetMapping("/cases")
    public AppResponse<List<OperationsService.CaseSummary>> cases(
            @RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey) {
        keys.requireOperations(apiKey);
        return AppResponse.success(service.openCases());
    }
}
