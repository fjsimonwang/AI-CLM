package com.acme.clm.api;

import com.acme.clm.config.CurrentUser;
import com.acme.clm.service.WorkflowService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    private final WorkflowService service;
    private final CurrentUser current;

    public WorkflowController(WorkflowService service, CurrentUser current) {
        this.service = service;
        this.current = current;
    }

    @GetMapping("/my-tasks")
    public List<Map<String, Object>> myTasks() { return service.myTasks(current.id()); }

    @GetMapping("/tasks")
    public List<Map<String, Object>> allTasks() { return service.allOpenTasks(); }

    @GetMapping("/status/{contractId}")
    public Map<String, Object> status(@PathVariable UUID contractId) { return service.status(contractId); }

    @PostMapping("/start/{contractId}")
    public Map<String, Object> start(@PathVariable UUID contractId) {
        service.start(contractId, current.id());
        return service.status(contractId);
    }

    public record ActRequest(String event, String comment) {}

    @PostMapping("/tasks/{taskId}/act")
    public Map<String, Object> act(@PathVariable UUID taskId, @RequestBody ActRequest req) {
        return service.act(taskId, req.event(), req.comment(), current.id());
    }
}
