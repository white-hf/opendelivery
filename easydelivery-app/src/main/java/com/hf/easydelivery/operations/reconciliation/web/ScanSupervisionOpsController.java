package com.hf.easydelivery.operations.reconciliation.web;

import com.hf.easydelivery.common.response.AppResponse;
import com.hf.easydelivery.operations.reconciliation.service.ScanSupervisionService;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@Profile("!memory")
@RequestMapping("/ops/v1")
public class ScanSupervisionOpsController {
    private final ScanSupervisionService service;

    public ScanSupervisionOpsController(ScanSupervisionService service) {
        this.service = service;
    }

    @GetMapping("/scan-supervision")
    public AppResponse<?> supervision(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @RequestParam(required = false) Long waveId) {
        return AppResponse.success(service.supervision(serviceDate, waveId));
    }

    @GetMapping("/scan-sessions")
    public AppResponse<?> sessions(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate) {
        return AppResponse.success(service.sessions(taskId, status, serviceDate));
    }

    @GetMapping("/scan-sessions/{sessionId}/events")
    public AppResponse<?> sessionEvents(@PathVariable long sessionId) {
        return AppResponse.success(service.sessionEvents(sessionId));
    }
}
