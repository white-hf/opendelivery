package com.hf.easydelivery.operations.arrival.web;

import com.hf.easydelivery.common.response.AppResponse;
import com.hf.easydelivery.operations.OperationsService;
import com.hf.easydelivery.operations.arrival.InboundOperationsService;
import com.hf.easydelivery.operations.arrival.ManifestReceiptRequest;
import com.hf.easydelivery.operations.arrival.PhysicalArrivalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!memory")
@RequestMapping("/ops/v1")
public class ArrivalOpsController {
    private final PhysicalArrivalService physicalArrival;
    private final InboundOperationsService inbound;
    private final OperationsService service;

    public ArrivalOpsController(PhysicalArrivalService physicalArrival,
                                InboundOperationsService inbound,
                                OperationsService service) {
        this.physicalArrival = physicalArrival;
        this.inbound = inbound;
        this.service = service;
    }

    @GetMapping("/arrival-trips")
    public AppResponse<?> arrivalTrips(@RequestParam java.time.LocalDate serviceDate) {
        return AppResponse.success(physicalArrival.trips(serviceDate));
    }

    @PostMapping("/arrival-trips")
    public AppResponse<?> createArrivalTrip(@RequestBody PhysicalArrivalService.TripRequest body,
                                             HttpServletRequest request) {
        return AppResponse.success("Arrival trip created", physicalArrival.createTrip(body, request));
    }

    @GetMapping("/arrival-trips/{tripId}")
    public AppResponse<?> arrivalTrip(@PathVariable long tripId) {
        return AppResponse.success(physicalArrival.detail(tripId));
    }

    @PostMapping("/arrival-trips/{tripId}/state")
    public AppResponse<?> moveArrivalTrip(@PathVariable long tripId,
                                          @RequestBody PhysicalArrivalService.StateRequest body,
                                          HttpServletRequest request) {
        return AppResponse.success("Arrival trip updated", physicalArrival.moveTrip(tripId, body, request));
    }

    @PostMapping("/arrival-trips/{tripId}/handling-units")
    public AppResponse<?> createHandlingUnit(@PathVariable long tripId,
                                              @RequestBody PhysicalArrivalService.UnitRequest body,
                                              HttpServletRequest request) {
        return AppResponse.success("Handling unit created", physicalArrival.createUnit(tripId, body, request));
    }

    @PostMapping("/handling-units/{unitId}/area-fill")
    public AppResponse<?> areaFillHandlingUnit(@PathVariable long unitId,
                                                @RequestBody PhysicalArrivalService.AreaFillRequest body,
                                                HttpServletRequest request) {
        return AppResponse.success("Handling unit area-filled", physicalArrival.areaFill(unitId, body, request));
    }

    @PostMapping("/handling-units/{unitId}/state")
    public AppResponse<?> moveHandlingUnit(@PathVariable long unitId,
                                            @RequestBody PhysicalArrivalService.StateRequest body,
                                            HttpServletRequest request) {
        return AppResponse.success("Handling unit updated", physicalArrival.moveUnit(unitId, body, request));
    }

    @PostMapping("/manifests/{manifestNo}/receipts")
    public AppResponse<OperationsService.ReceiptResult> receive(
            @RequestHeader(value = "X-Ops-Api-Key", required = false) String apiKey,
            @PathVariable String manifestNo,
            @Valid @RequestBody ManifestReceiptRequest request) {
        return AppResponse.success("Parcel received", service.receive(manifestNo, request));
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
                                       HttpServletRequest httpRequest) {
        return AppResponse.success("Scan classified", inbound.scan(manifestId, request, httpRequest));
    }

    @PostMapping("/manifests/{manifestId}/discrepancies/{itemId}/decisions")
    public AppResponse<?> decideDiscrepancy(@PathVariable long manifestId, @PathVariable long itemId,
                                            @RequestBody InboundOperationsService.DecisionRequest request,
                                            HttpServletRequest httpRequest) {
        inbound.resolveDiscrepancy(manifestId, itemId, request, httpRequest);
        return AppResponse.success("Discrepancy resolved", null);
    }

    @PostMapping("/manifests/{manifestId}/close")
    public AppResponse<?> closeManifest(@PathVariable long manifestId,
                                        @RequestBody InboundOperationsService.CloseRequest request,
                                        HttpServletRequest httpRequest) {
        return AppResponse.success("Manifest closed", inbound.close(manifestId, request, httpRequest));
    }
}
