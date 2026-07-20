package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.response.AppResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;

import java.util.List;

@RestController
@Profile("!memory")
@RequestMapping("/ops/v1")
public class OperationsController {
    private final OperationsService service;
    private final RoutingOperationsService routing;

    public OperationsController(OperationsService service, RoutingOperationsService routing) {
        this.service = service;
        this.routing = routing;
    }

    @PostMapping("/manifests/{manifestNo}/receipts")
    public AppResponse<OperationsService.ReceiptResult> receive(
            @RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey,
            @PathVariable String manifestNo,
            @Valid @RequestBody ManifestReceiptRequest request) {
        return AppResponse.success("Parcel received", service.receive(manifestNo, request));
    }

    @PostMapping("/waves")
    public AppResponse<OperationsService.WaveResult> createWave(
            @RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey,
            @Valid @RequestBody CreateWaveRequest request) {
        return AppResponse.success("Wave published", service.createAndPublishWave(request));
    }

    @GetMapping("/cases")
    public AppResponse<List<OperationsService.CaseSummary>> cases(
            @RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey) {
        return AppResponse.success(service.openCases());
    }

    @GetMapping("/stations")
    public AppResponse<?> stations(@RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey) {
        return AppResponse.success(routing.stations());
    }

    @PostMapping("/stations")
    public AppResponse<?> createStation(@RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey,
                                        @RequestBody RoutingOperationsService.StationRequest request) {
        return AppResponse.success("Station created", routing.createStation(request));
    }

    @GetMapping("/station-service-areas")
    public AppResponse<?> serviceAreas(@RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey) {
        return AppResponse.success(routing.serviceAreas());
    }

    @GetMapping("/readiness")
    public AppResponse<?> readiness(@RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey) {
        return AppResponse.success(routing.readiness());
    }

    @PostMapping("/station-service-areas")
    public AppResponse<?> createServiceArea(@RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey,
                                             @RequestBody RoutingOperationsService.ServiceAreaRequest request) {
        return AppResponse.success("Service area created", routing.createServiceArea(request));
    }

    @PostMapping("/waybills/{waybillId}/route")
    public AppResponse<?> reroute(@RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey,
                                  @PathVariable long waybillId) {
        return AppResponse.success("Routing completed", routing.reroute(waybillId));
    }

    @PostMapping("/waybills/{waybillId}/routing-override")
    public AppResponse<?> override(@RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey,
                                   @PathVariable long waybillId,
                                   @RequestBody RoutingOperationsService.RoutingOverrideRequest request) {
        return AppResponse.success("Routing overridden", routing.override(waybillId, request));
    }
}
