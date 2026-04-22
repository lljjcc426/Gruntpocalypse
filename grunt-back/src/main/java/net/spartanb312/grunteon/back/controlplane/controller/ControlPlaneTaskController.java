package net.spartanb312.grunteon.back.controlplane.controller;

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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public class ControlPlaneTaskController {

    private final ControlPlaneTaskFacade facade;

    public ControlPlaneTaskController(ControlPlaneTaskFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/api/control/tasks")
    public Map<String, Object> createTask(@RequestBody Map<String, Object> request, Authentication authentication) {
        return facade.createTask(request, false, authentication);
    }

    @GetMapping("/api/control/tasks")
    public Mono<Map<String, Object>> listTasks(Authentication authentication) {
        return Mono.fromCallable(() -> facade.listTasks(authentication)).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/api/control/tasks/{taskId}")
    public Mono<Map<String, Object>> getTask(@PathVariable String taskId, Authentication authentication) {
        return Mono.fromCallable(() -> facade.getTask(taskId, authentication)).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/api/control/tasks/{taskId}/artifacts")
    public Mono<Map<String, Object>> artifacts(@PathVariable String taskId, Authentication authentication) {
        return Mono.fromCallable(() -> facade.artifacts(taskId, authentication)).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/api/control/tasks/{taskId}/actions/start")
    public Map<String, Object> startTask(@PathVariable String taskId, Authentication authentication) {
        return facade.startTask(taskId, authentication);
    }

    @GetMapping("/api/control/tasks/{taskId}/stages")
    public Mono<Map<String, Object>> stages(@PathVariable String taskId, Authentication authentication) {
        return Mono.fromCallable(() -> facade.stages(taskId, authentication)).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/api/control/tasks/{taskId}/logs")
    public Mono<Map<String, Object>> logs(@PathVariable String taskId, Authentication authentication) {
        return Mono.fromCallable(() -> facade.logs(taskId, authentication)).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/api/control/tasks/{taskId}/artifacts/output-url")
    public Mono<Map<String, Object>> downloadUrl(@PathVariable String taskId, Authentication authentication) {
        return Mono.fromCallable(() -> facade.downloadUrl(taskId, authentication)).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/api/control/tasks/{taskId}/artifacts/output")
    public ResponseEntity<Resource> download(@PathVariable String taskId, Authentication authentication) {
        return facade.download(taskId, authentication);
    }

    @GetMapping("/api/control/tasks/{taskId}/project/meta")
    public Map<String, Object> projectMeta(@PathVariable String taskId, @RequestParam("scope") String scopeValue, Authentication authentication) {
        return facade.projectMeta(taskId, scopeValue, authentication);
    }

    @GetMapping("/api/control/tasks/{taskId}/project/tree")
    public Map<String, Object> projectTree(@PathVariable String taskId, @RequestParam("scope") String scopeValue, Authentication authentication) {
        return facade.projectTree(taskId, scopeValue, authentication);
    }

    @GetMapping("/api/control/tasks/{taskId}/project/source")
    public Map<String, Object> projectSource(
        @PathVariable String taskId,
        @RequestParam("scope") String scopeValue,
        @RequestParam("class") String className,
        Authentication authentication
    ) {
        return facade.projectSource(taskId, scopeValue, className, authentication);
    }
}
