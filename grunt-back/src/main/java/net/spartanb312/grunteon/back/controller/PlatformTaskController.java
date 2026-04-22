package net.spartanb312.grunteon.back.controller;

import java.util.Map;
import net.spartanb312.grunteon.back.controlplane.ControlPlaneTaskFacade;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlatformTaskController {

    private final ControlPlaneTaskFacade facade;

    public PlatformTaskController(ControlPlaneTaskFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/api/v1/tasks")
    public Map<String, Object> createTask(@RequestBody Map<String, Object> request, Authentication authentication) {
        return facade.createTask(request, true, authentication);
    }

    @GetMapping("/api/v1/tasks")
    public Map<String, Object> listTasks(Authentication authentication) {
        return facade.listTasks(authentication);
    }

    @GetMapping("/api/v1/tasks/{taskId}")
    public Map<String, Object> getTask(@PathVariable String taskId, Authentication authentication) {
        return facade.getTask(taskId, authentication);
    }

    @PostMapping("/api/v1/tasks/{taskId}/start")
    public Map<String, Object> startTask(@PathVariable String taskId, Authentication authentication) {
        return facade.startTask(taskId, authentication);
    }

    @GetMapping("/api/v1/tasks/{taskId}/stages")
    public Map<String, Object> stages(@PathVariable String taskId, Authentication authentication) {
        return facade.stages(taskId, authentication);
    }

    @GetMapping("/api/v1/tasks/{taskId}/logs")
    public Map<String, Object> logs(@PathVariable String taskId, Authentication authentication) {
        return facade.logs(taskId, authentication);
    }

    @GetMapping("/api/v1/tasks/{taskId}/download-url")
    public Map<String, Object> downloadUrl(@PathVariable String taskId, Authentication authentication) {
        return facade.downloadUrl(taskId, authentication);
    }

    @GetMapping("/api/v1/tasks/{taskId}/download")
    public ResponseEntity<Resource> download(@PathVariable String taskId, Authentication authentication) {
        return facade.download(taskId, authentication);
    }

    @GetMapping("/api/v1/tasks/{taskId}/project/meta")
    public Map<String, Object> projectMeta(@PathVariable String taskId, @RequestParam("scope") String scopeValue, Authentication authentication) {
        return facade.projectMeta(taskId, scopeValue, authentication);
    }

    @GetMapping("/api/v1/tasks/{taskId}/project/tree")
    public Map<String, Object> projectTree(@PathVariable String taskId, @RequestParam("scope") String scopeValue, Authentication authentication) {
        return facade.projectTree(taskId, scopeValue, authentication);
    }

    @GetMapping("/api/v1/tasks/{taskId}/project/source")
    public Map<String, Object> projectSource(
        @PathVariable String taskId,
        @RequestParam("scope") String scopeValue,
        @RequestParam("class") String className,
        Authentication authentication
    ) {
        return facade.projectSource(taskId, scopeValue, className, authentication);
    }
}
