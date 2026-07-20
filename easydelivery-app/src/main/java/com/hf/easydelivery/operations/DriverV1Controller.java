package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.response.AppResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!memory")
@RequestMapping("/driver/v1")
public class DriverV1Controller {
    private final FailureReturnService service;
    public DriverV1Controller(FailureReturnService service){this.service=service;}
    @GetMapping("/failure-reasons") public AppResponse<?> reasons(){return AppResponse.success(service.reasons());}
    @PostMapping("/task-items/{itemId}/attempts") public AppResponse<?> attempt(@PathVariable long itemId,@RequestBody FailureReturnService.AttemptRequest body,HttpServletRequest req){return AppResponse.success("Attempt recorded",service.attempt(itemId,driver(req),body));}
    @GetMapping("/tasks/{taskId}/closeout") public AppResponse<?> closeout(@PathVariable long taskId,HttpServletRequest req){return AppResponse.success(service.closeout(taskId,driver(req)));}
    @PostMapping("/tasks/{taskId}/return-sessions") public AppResponse<?> openReturn(@PathVariable long taskId,HttpServletRequest req){return AppResponse.success("Return session opened",service.openReturn(taskId,driver(req)));}
    @PostMapping("/return-sessions/{sessionId}/events") public AppResponse<?> scan(@PathVariable long sessionId,@RequestBody FailureReturnService.ScanRequest body,HttpServletRequest req){return AppResponse.success("Return scan classified",service.scanReturn(sessionId,driver(req),body));}
    @PostMapping("/return-sessions/{sessionId}/submit") public AppResponse<?> submit(@PathVariable long sessionId,HttpServletRequest req){service.submitReturn(sessionId,driver(req));return AppResponse.success("Return submitted",null);}
    private int driver(HttpServletRequest req){return (Integer)req.getAttribute("driverId");}
}
