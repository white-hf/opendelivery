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
    private final InboundOperationsService inbound;
    private final DispatchOperationsService dispatch;
    private final FailureReturnService failureReturn;

    public OperationsController(OperationsService service, RoutingOperationsService routing,
                                InboundOperationsService inbound, DispatchOperationsService dispatch,
                                FailureReturnService failureReturn) {
        this.service = service;
        this.routing = routing;
        this.inbound = inbound;
        this.dispatch = dispatch;
        this.failureReturn = failureReturn;
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

    @GetMapping("/manifests")
    public AppResponse<?> manifests(@RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "50") int limit,
                                    @RequestParam(defaultValue = "0") long afterId) {
        return AppResponse.success(inbound.manifests(status, limit, afterId));
    }

    @GetMapping("/manifests/{manifestId}")
    public AppResponse<?> manifest(@PathVariable long manifestId) {
        return AppResponse.success(inbound.detail(manifestId));
    }

    @PostMapping("/manifests/{manifestId}/start")
    public AppResponse<?> startManifest(@PathVariable long manifestId) {
        return AppResponse.success("Receiving started", inbound.start(manifestId));
    }

    @PostMapping("/manifests/{manifestId}/scan-events")
    public AppResponse<?> scanManifest(@PathVariable long manifestId,
                                       @RequestBody InboundOperationsService.ScanRequest request,
                                       jakarta.servlet.http.HttpServletRequest httpRequest) {
        return AppResponse.success("Scan classified", inbound.scan(manifestId, request, httpRequest));
    }

    @PostMapping("/manifests/{manifestId}/discrepancies/{itemId}/decisions")
    public AppResponse<?> decideDiscrepancy(@PathVariable long manifestId, @PathVariable long itemId,
                                            @RequestBody InboundOperationsService.DecisionRequest request,
                                            jakarta.servlet.http.HttpServletRequest httpRequest) {
        inbound.resolveDiscrepancy(manifestId, itemId, request, httpRequest);
        return AppResponse.success("Discrepancy resolved", null);
    }

    @PostMapping("/manifests/{manifestId}/close")
    public AppResponse<?> closeManifest(@PathVariable long manifestId,
                                        @RequestBody InboundOperationsService.CloseRequest request,
                                        jakarta.servlet.http.HttpServletRequest httpRequest) {
        return AppResponse.success("Manifest closed", inbound.close(manifestId, request, httpRequest));
    }

    @GetMapping("/dispatch/candidates")
    public AppResponse<?> dispatchCandidates(@RequestParam(defaultValue = "50") int limit,
                                             @RequestParam(defaultValue = "0") long afterId) {
        return AppResponse.success(dispatch.candidates(limit, afterId));
    }

    @PostMapping("/dispatch/waves")
    public AppResponse<?> createWaveDraft(@RequestBody DispatchOperationsService.DraftRequest request) {
        return AppResponse.success("Wave draft created", dispatch.createDraft(request));
    }

    @PostMapping("/dispatch/waves/{waveId}/publish")
    public AppResponse<?> publishWave(@PathVariable long waveId, jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Wave published", dispatch.publish(waveId, request));
    }

    @PostMapping("/dispatch/waves/{waveId}/revoke")
    public AppResponse<?> revokeWave(@PathVariable long waveId) {
        dispatch.revoke(waveId);
        return AppResponse.success("Wave revoked", null);
    }

    @PostMapping("/scan-sessions/{sessionId}/approve")
    public AppResponse<?> approveLoad(@PathVariable long sessionId, jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Load handover approved", dispatch.approveLoad(sessionId, request));
    }

    @PostMapping("/return-sessions/{sessionId}/decision")
    public AppResponse<?> approveReturn(@PathVariable long sessionId,
                                        @RequestBody FailureReturnService.ReturnDecision decision,
                                        jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Return handover approved", failureReturn.approveReturn(sessionId, decision, request));
    }
}
