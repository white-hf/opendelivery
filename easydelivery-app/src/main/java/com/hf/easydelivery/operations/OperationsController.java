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
    private final RoutingOperationsService routing;

    public OperationsController(ApiKeyVerifier keys, OperationsService service, RoutingOperationsService routing) {
        this.keys = keys;
        this.service = service;
        this.routing = routing;
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

    @GetMapping("/stations")
    public AppResponse<?> stations(@RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey) {
        keys.requireOperations(apiKey);
        return AppResponse.success(routing.stations());
    }

    @PostMapping("/stations")
    public AppResponse<?> createStation(@RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey,
                                        @RequestBody RoutingOperationsService.StationRequest request) {
        keys.requireOperations(apiKey);
        return AppResponse.success("Station created", routing.createStation(request));
    }

    @GetMapping("/station-service-areas")
    public AppResponse<?> serviceAreas(@RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey) {
        keys.requireOperations(apiKey);
        return AppResponse.success(routing.serviceAreas());
    }

    @PostMapping("/station-service-areas")
    public AppResponse<?> createServiceArea(@RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey,
                                             @RequestBody RoutingOperationsService.ServiceAreaRequest request) {
        keys.requireOperations(apiKey);
        return AppResponse.success("Service area created", routing.createServiceArea(request));
    }

    @PostMapping("/waybills/{waybillId}/route")
    public AppResponse<?> reroute(@RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey,
                                  @PathVariable long waybillId) {
        keys.requireOperations(apiKey);
        return AppResponse.success("Routing completed", routing.reroute(waybillId));
    }

    @PostMapping("/waybills/{waybillId}/routing-override")
    public AppResponse<?> override(@RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey,
                                   @PathVariable long waybillId,
                                   @RequestBody RoutingOperationsService.RoutingOverrideRequest request) {
        keys.requireOperations(apiKey);
        return AppResponse.success("Routing overridden", routing.override(waybillId, request));
    }
}
