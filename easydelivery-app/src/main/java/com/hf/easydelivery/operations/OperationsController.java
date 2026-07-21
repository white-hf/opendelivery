package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.response.AppResponse;
import com.hf.easydelivery.operations.station.RoutingOperationsService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Profile("!memory")
@RequestMapping("/ops/v1")
public class OperationsController {
    private final OperationsService service;
    private final RoutingOperationsService routing;
    private final DispatchOperationsService dispatch;
    private final FailedParcelReturnService failedReturns;
    private final DeliveryAreaOperationsService deliveryAreas;
    private final MapPlanningService planning;
    private final ControlTowerService controlTower;
    private final DeliverySupervisionService supervision;
    private final DayCloseOperationsService dayClose;
    private final ConfigCaseOperationsService configCase;

    public OperationsController(OperationsService service, RoutingOperationsService routing,
                                DispatchOperationsService dispatch,
                                FailedParcelReturnService failedReturns, DeliveryAreaOperationsService deliveryAreas,
                                MapPlanningService planning, ControlTowerService controlTower,
                                DeliverySupervisionService supervision, DayCloseOperationsService dayClose,
                                ConfigCaseOperationsService configCase) {
        this.service = service;
        this.routing = routing;
        this.dispatch = dispatch;
        this.failedReturns = failedReturns;
        this.deliveryAreas = deliveryAreas;
        this.planning = planning;
        this.controlTower = controlTower;
        this.supervision = supervision;
        this.dayClose = dayClose;
        this.configCase = configCase;
    }

    @GetMapping("/control-tower")
    public AppResponse<?> controlTower(@RequestParam java.time.LocalDate serviceDate) {
        return AppResponse.success(controlTower.snapshot(serviceDate));
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

    @GetMapping("/dispatch/candidates")
    public AppResponse<?> dispatchCandidates(@RequestParam(defaultValue = "50") int limit,
                                             @RequestParam(defaultValue = "0") long afterId) {
        return AppResponse.success(dispatch.candidates(limit, afterId));
    }

    @GetMapping("/dispatch/drivers")
    public AppResponse<?> activeDrivers() {
        return AppResponse.success(dispatch.activeDrivers());
    }

    @GetMapping("/dispatch/waves")
    public AppResponse<?> dispatchWaves(@RequestParam(defaultValue = "50") int limit,
                                        @RequestParam(defaultValue = "0") long afterId) {
        return AppResponse.success(dispatch.waves(limit, afterId));
    }

    @PostMapping("/dispatch/waves")
    public AppResponse<?> createWaveDraft(@RequestBody DispatchOperationsService.DraftRequest request) {
        return AppResponse.success("Wave draft created", dispatch.createDraft(request));
    }

    @PostMapping("/dispatch/waves/{waveId}/publish")
    public AppResponse<?> publishWave(@PathVariable long waveId, jakarta.servlet.http.HttpServletRequest request) {
        dispatch.publish(waveId, request);
        return AppResponse.success("Wave published", null);
    }

    @PostMapping("/dispatch/waves/{waveId}/revoke")
    public AppResponse<?> revokeWave(@PathVariable long waveId) {
        dispatch.revoke(waveId);
        return AppResponse.success("Wave revoked", null);
    }

    @GetMapping("/planning/shifts")
    public AppResponse<?> planningShifts(@RequestParam java.time.LocalDate serviceDate) {
        return AppResponse.success(planning.shifts(serviceDate));
    }

    @PutMapping("/planning/shifts")
    public AppResponse<?> savePlanningShift(@RequestBody MapPlanningService.ShiftRequest body) {
        return AppResponse.success("Driver shift saved", planning.saveShift(body));
    }

    @GetMapping("/planning/parcels")
    public AppResponse<?> planningParcels(@RequestParam java.time.LocalDate serviceDate,
            @RequestParam(required=false) Double west,@RequestParam(required=false) Double south,
            @RequestParam(required=false) Double east,@RequestParam(required=false) Double north,
            @RequestParam(defaultValue="1000") int limit) {
        return AppResponse.success(planning.mapParcels(serviceDate,west,south,east,north,limit));
    }

    @PostMapping("/planning/waves")
    public AppResponse<?> createPlanningWave(@RequestBody MapPlanningService.WaveRequest body) {
        return AppResponse.success("Planning wave created", planning.createWave(body));
    }

    @GetMapping("/planning/waves/{waveId}")
    public AppResponse<?> planningWave(@PathVariable long waveId) {
        return AppResponse.success(planning.waveSummary(waveId));
    }

    @GetMapping("/planning/unplanned")
    public AppResponse<?> unplannedParcels(@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate serviceDate) {
        return AppResponse.success(planning.unplannedParcels(serviceDate));
    }

    @PostMapping("/planning/waves/{waveId}/assignments")
    public AppResponse<?> assignPlanningWave(@PathVariable long waveId,
            @RequestBody MapPlanningService.AssignmentRequest body, jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Parcels assigned", planning.assign(waveId,body,request));
    }

    @PostMapping("/planning/waves/{waveId}/assign-defaults")
    public AppResponse<?> assignDefaultsPlanningWave(@PathVariable long waveId, jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Default parcel assignments applied", planning.assignDefaults(waveId, request));
    }

    @PostMapping("/planning/waves/{waveId}/parcels/{parcelId}/reassign")
    public AppResponse<?> reassignPlanningParcel(@PathVariable long waveId,@PathVariable long parcelId,
            @RequestBody MapPlanningService.ReassignRequest body, jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Parcel reassigned", planning.reassign(waveId,parcelId,body,request));
    }

    @PostMapping("/planning/waves/{waveId}/freeze")
    public AppResponse<?> freezePlanningWave(@PathVariable long waveId,
            @RequestBody MapPlanningService.ReasonRequest body, jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Planning wave frozen", planning.freeze(waveId,body,request));
    }

    @PostMapping("/planning/waves/{waveId}/publish")
    public AppResponse<?> publishPlanningWave(@PathVariable long waveId,
            @RequestBody MapPlanningService.ReasonRequest body, jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Planning wave published", planning.publish(waveId,body,request));
    }

    @PostMapping("/scan-sessions/{sessionId}/approve")
    public AppResponse<?> approveLoad(@PathVariable long sessionId, jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Load handover approved", dispatch.approveLoad(sessionId, request));
    }

    @PostMapping("/scan-sessions/{sessionId}/reject")
    public AppResponse<?> rejectLoad(@PathVariable long sessionId, @RequestBody(required=false) MapPlanningService.ReasonRequest body, jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Load handover rejected to reopen", dispatch.rejectSession(sessionId, body, request));
    }

    @GetMapping("/outbox")
    public AppResponse<?> outboxEvents(@RequestParam(required=false) String status, @RequestParam(defaultValue="50") int limit) {
        return AppResponse.success(configCase.listOutboxEvents(status, limit));
    }

    @PostMapping("/outbox/{eventId}/replay")
    public AppResponse<?> replayOutboxEvent(@PathVariable long eventId, jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Outbox event replayed", configCase.replayOutboxEvent(eventId, request));
    }

    @PostMapping("/cases/{caseId}/actions")
    public AppResponse<?> addCaseAction(@PathVariable long caseId,
            @RequestBody ConfigCaseOperationsService.CaseActionRequest body,
            jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Case action logged", configCase.addCaseAction(caseId, body, request));
    }

    @GetMapping("/audit-logs")
    public AppResponse<?> auditLogs(@RequestParam(required=false) String resourceType,
            @RequestParam(required=false) String resourceId,
            @RequestParam(defaultValue="50") int limit) {
        return AppResponse.success(configCase.listAuditLogs(resourceType, resourceId, limit));
    }

    @GetMapping("/day-close")
    public AppResponse<?> dayClose(@RequestParam java.time.LocalDate serviceDate) {
        return AppResponse.success(dayClose.getReconciliation(serviceDate));
    }

    @PostMapping("/day-close/recalculate")
    public AppResponse<?> recalculateDayClose(@RequestParam java.time.LocalDate serviceDate, jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Day close recalculated", dayClose.recalculate(serviceDate, request));
    }

    @PostMapping("/day-close/sign")
    public AppResponse<?> signDayClose(@RequestParam java.time.LocalDate serviceDate,
            @RequestBody(required=false) DayCloseOperationsService.SignOffRequest body,
            jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Day close signed off", dayClose.signOff(serviceDate, body, request));
    }

    @GetMapping("/delivery-monitor")
    public AppResponse<?> deliveryMonitor(@RequestParam java.time.LocalDate serviceDate) {
        return AppResponse.success(supervision.monitor(serviceDate));
    }

    @PostMapping("/delivery-monitor/parcels/{parcelId}/approve-hold")
    public AppResponse<?> approveDeliveryHold(@PathVariable long parcelId,
            @RequestBody(required=false) DeliverySupervisionService.HoldRequest body,
            jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Driver hold approved", supervision.approveHold(parcelId, body, request));
    }

    @PostMapping("/delivery-monitor/parcels/{parcelId}/redispatch")
    public AppResponse<?> redispatchParcel(@PathVariable long parcelId,
            @RequestBody DeliverySupervisionService.RedispatchRequest body,
            jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Parcel redispatched", supervision.redispatch(parcelId, body, request));
    }

    @GetMapping("/failed-returns")
    public AppResponse<?> failedReturns(@RequestParam java.time.LocalDate serviceDate) {
        return AppResponse.success(failedReturns.pending(serviceDate));
    }

    @PostMapping("/failed-returns/{parcelId}/receive")
    public AppResponse<?> receiveFailedReturn(@PathVariable long parcelId,
            @RequestBody FailedParcelReturnService.ReceiptRequest body,
            jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Failed parcel returned to station", failedReturns.receive(parcelId, body, request));
    }

    @GetMapping("/delivery-areas")
    public AppResponse<?> deliveryAreas() { return AppResponse.success(deliveryAreas.areas()); }

    @GetMapping("/delivery-areas/{areaId}/versions")
    public AppResponse<?> deliveryAreaVersions(@PathVariable long areaId) { return AppResponse.success(deliveryAreas.versions(areaId)); }

    @GetMapping("/delivery-areas/{areaId}/driver-preferences")
    public AppResponse<?> deliveryAreaDriverPreferences(@PathVariable long areaId) {
        return AppResponse.success(deliveryAreas.driverPreferences(areaId));
    }

    @PostMapping("/delivery-areas/{areaId}/driver-preferences")
    public AppResponse<?> saveDeliveryAreaDriverPreference(@PathVariable long areaId,
            @RequestBody DeliveryAreaOperationsService.DriverPreferenceRequest body,
            jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Driver area preference saved",deliveryAreas.saveDriverPreference(areaId,body,request));
    }

    @PostMapping("/parcels/area-recompute")
    public AppResponse<?> recomputeParcelAreas(
            @RequestBody(required = false) DeliveryAreaOperationsService.AreaRecomputeRequest body,
            jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Parcel areas recomputed", deliveryAreas.recomputeAreas(body, request));
    }

    @PostMapping("/parcels/{parcelId}/area-match")
    public AppResponse<?> matchParcelArea(@PathVariable long parcelId,
            @RequestBody DeliveryAreaOperationsService.ParcelLocationRequest body,
            jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Parcel matched to delivery area",deliveryAreas.matchParcel(parcelId,body,request));
    }

    @PostMapping("/parcels/{parcelId}/area-override")
    public AppResponse<?> overrideParcelArea(@PathVariable long parcelId,
            @RequestBody DeliveryAreaOperationsService.ParcelAreaOverrideRequest body,
            jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Parcel area overridden",deliveryAreas.overrideParcelArea(parcelId,body,request));
    }

    @PostMapping("/delivery-areas")
    public AppResponse<?> createDeliveryArea(@RequestBody DeliveryAreaOperationsService.CreateRequest body,
                                             jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Delivery area draft created",deliveryAreas.create(body,request));
    }

    @PostMapping("/delivery-areas/{areaId}/versions")
    public AppResponse<?> createDeliveryAreaVersion(@PathVariable long areaId,
                                                     @RequestBody DeliveryAreaOperationsService.VersionRequest body,
                                                     jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Delivery area version created",deliveryAreas.createVersion(areaId,body,request));
    }

    @PutMapping("/delivery-areas/{areaId}")
    public AppResponse<?> updateDeliveryArea(@PathVariable long areaId,
            @RequestBody DeliveryAreaOperationsService.UpdateRequest body,
            jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Delivery area updated",deliveryAreas.update(areaId,body,request));
    }

    @DeleteMapping("/delivery-areas/{areaId}")
    public AppResponse<?> deactivateDeliveryArea(@PathVariable long areaId,
            @RequestBody DeliveryAreaOperationsService.StateChangeRequest body,
            jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Delivery area deactivated",deliveryAreas.deactivate(areaId,body,request));
    }

    @PostMapping("/delivery-areas/{areaId}/activate")
    public AppResponse<?> activateDeliveryArea(@PathVariable long areaId,
            @RequestBody DeliveryAreaOperationsService.StateChangeRequest body,
            jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Delivery area activated",deliveryAreas.activate(areaId,body,request));
    }

    @PostMapping("/delivery-areas/{areaId}/versions/{versionId}/validate")
    public AppResponse<?> validateDeliveryArea(@PathVariable long areaId,@PathVariable long versionId,
                                               jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Delivery area validated",deliveryAreas.validate(areaId,versionId,request));
    }

    @PostMapping("/delivery-areas/{areaId}/versions/{versionId}/publish")
    public AppResponse<?> publishDeliveryArea(@PathVariable long areaId,@PathVariable long versionId,
                                              @RequestBody DeliveryAreaOperationsService.PublishRequest body,
                                              jakarta.servlet.http.HttpServletRequest request) {
        return AppResponse.success("Delivery area published",deliveryAreas.publish(areaId,versionId,body,request));
    }
}
