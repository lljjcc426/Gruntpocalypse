package net.spartanb312.grunteon.back.controlplane.controller;

import java.util.Map;
import net.spartanb312.grunteon.back.controlplane.ControlPlaneTaskFacade;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControlPlaneTaskController {

    private final ControlPlaneTaskFacade facade;

    public ControlPlaneTaskController(ControlPlaneTaskFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/api/control/tasks")
    public Map<String, Object> createTask(@RequestBody Map<String, Object> request) {
        return facade.createTask(request, false);
    }

    @GetMapping("/api/control/tasks")
    public Map<String, Object> listTasks() {
        return facade.listTasks();
    }

    @GetMapping("/api/control/tasks/{taskId}")
    public Map<String, Object> getTask(@PathVariable String taskId) {
        return facade.getTask(taskId);
    }

    @PostMapping("/api/control/tasks/{taskId}/actions/start")
    public Map<String, Object> startTask(@PathVariable String taskId) {
        return facade.startTask(taskId);
    }

    @GetMapping("/api/control/tasks/{taskId}/stages")
    public Map<String, Object> stages(@PathVariable String taskId) {
        return facade.stages(taskId);
    }

    @GetMapping("/api/control/tasks/{taskId}/logs")
    public Map<String, Object> logs(@PathVariable String taskId) {
        return facade.logs(taskId);
    }

    @GetMapping("/api/control/tasks/{taskId}/artifacts/output-url")
    public Map<String, Object> downloadUrl(@PathVariable String taskId) {
        return facade.downloadUrl(taskId);
    }

    @GetMapping("/api/control/tasks/{taskId}/artifacts/output")
    public ResponseEntity<Resource> download(@PathVariable String taskId) {
        return facade.download(taskId);
    }

    @GetMapping("/api/control/tasks/{taskId}/project/meta")
    public Map<String, Object> projectMeta(@PathVariable String taskId, @RequestParam("scope") String scopeValue) {
        return facade.projectMeta(taskId, scopeValue);
    }

    @GetMapping("/api/control/tasks/{taskId}/project/tree")
    public Map<String, Object> projectTree(@PathVariable String taskId, @RequestParam("scope") String scopeValue) {
        return facade.projectTree(taskId, scopeValue);
    }

    @GetMapping("/api/control/tasks/{taskId}/project/source")
    public Map<String, Object> projectSource(
        @PathVariable String taskId,
        @RequestParam("scope") String scopeValue,
        @RequestParam("class") String className
    ) {
        return facade.projectSource(taskId, scopeValue, className);
    }
}
