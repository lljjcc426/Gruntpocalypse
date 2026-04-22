package net.spartanb312.grunteon.back.controlplane.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.spartanb312.grunteon.back.controlplane.ControlPlaneSessionFacade;
import net.spartanb312.grunteon.back.support.ApiSupport;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public class ControlPlaneSessionController {

    private final ControlPlaneSessionFacade facade;

    public ControlPlaneSessionController(ControlPlaneSessionFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/api/control/sessions")
    public Map<String, Object> createSession(@RequestBody(required = false) Map<String, Object> request, Authentication authentication) {
        String profile = request == null ? null : stringValue(request.get("profile"));
        return facade.createSession(profile, false, authentication);
    }

    @GetMapping("/api/control/sessions")
    public Mono<Map<String, Object>> listSessions(Authentication authentication) {
        return Mono.fromCallable(() -> facade.listSessions(authentication)).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/api/control/sessions/{sessionId}")
    public Mono<Map<String, Object>> status(@PathVariable String sessionId, Authentication authentication) {
        return Mono.fromCallable(() -> facade.status(sessionId, authentication)).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/api/control/sessions/{sessionId}/logs")
    public Mono<List<String>> logs(@PathVariable String sessionId, Authentication authentication) {
        return Mono.fromCallable(() -> facade.logs(sessionId, authentication)).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/api/control/sessions/{sessionId}/artifacts")
    public Mono<Map<String, Object>> artifacts(@PathVariable String sessionId, Authentication authentication) {
        return Mono.fromCallable(() -> facade.artifacts(sessionId, authentication)).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping(path = "/api/control/sessions/{sessionId}/artifacts/config", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> uploadConfig(
        @PathVariable String sessionId,
        @RequestPart("file") Mono<FilePart> filePartMono,
        Authentication authentication
    ) {
        facade.requireEditable(sessionId, authentication);
        return filePartMono.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "No config file uploaded")))
            .flatMap(filePart ->
                DataBufferUtils.join(filePart.content()).map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        String fileName = ApiSupport.sanitizeFileName(filePart.filename(), "config.json");
                        JsonObject json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                        return facade.configUploaded(sessionId, fileName, json);
                    } catch (Exception exception) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid config file");
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
            );
    }

    @PostMapping(path = "/api/control/sessions/{sessionId}/artifacts/input", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> uploadInput(
        @PathVariable String sessionId,
        @RequestPart("file") Mono<FilePart> filePartMono,
        Authentication authentication
    ) {
        File targetDir = facade.requireSession(sessionId, authentication).getInputDir();
        return filePartMono.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "No JAR uploaded")))
            .flatMap(filePart -> {
                String fileName = ApiSupport.sanitizeFileName(filePart.filename(), "input.jar");
                File target = ApiSupport.uniqueFile(targetDir, fileName);
                return filePart.transferTo(target.toPath()).then(Mono.fromCallable(() -> facade.inputUploaded(sessionId, target)));
            });
    }

    @PostMapping(path = "/api/control/sessions/{sessionId}/artifacts/libraries", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> uploadLibraries(
        @PathVariable String sessionId,
        @RequestPart("files") Flux<FilePart> files,
        Authentication authentication
    ) {
        return uploadFiles(sessionId, files, true, authentication);
    }

    @PostMapping(path = "/api/control/sessions/{sessionId}/artifacts/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> uploadAssets(
        @PathVariable String sessionId,
        @RequestPart("files") Flux<FilePart> files,
        Authentication authentication
    ) {
        return uploadFiles(sessionId, files, false, authentication);
    }

    @PostMapping("/api/control/sessions/{sessionId}/actions/obfuscate")
    public Map<String, Object> obfuscate(@PathVariable String sessionId, Authentication authentication) {
        return facade.obfuscate(sessionId, authentication);
    }

    @GetMapping("/api/control/sessions/{sessionId}/artifacts/output")
    public ResponseEntity<Resource> download(@PathVariable String sessionId, Authentication authentication) {
        return facade.download(sessionId, authentication);
    }

    @GetMapping("/api/control/sessions/{sessionId}/project/meta")
    public Map<String, Object> projectMeta(@PathVariable String sessionId, @RequestParam("scope") String scopeValue, Authentication authentication) {
        return facade.projectMeta(sessionId, scopeValue, authentication);
    }

    @GetMapping("/api/control/sessions/{sessionId}/project/tree")
    public Map<String, Object> projectTree(@PathVariable String sessionId, @RequestParam("scope") String scopeValue, Authentication authentication) {
        return facade.projectTree(sessionId, scopeValue, authentication);
    }

    @GetMapping("/api/control/sessions/{sessionId}/project/source")
    public Map<String, Object> projectSource(
        @PathVariable String sessionId,
        @RequestParam("scope") String scopeValue,
        @RequestParam("class") String className,
        Authentication authentication
    ) {
        return facade.projectSource(sessionId, scopeValue, className, authentication);
    }

    private Mono<Map<String, Object>> uploadFiles(String sessionId, Flux<FilePart> files, boolean libraries, Authentication authentication) {
        File targetDir = libraries
            ? facade.requireSession(sessionId, authentication).getLibrariesDir()
            : facade.requireSession(sessionId, authentication).getAssetsDir();
        String emptyMessage = libraries ? "No library files uploaded" : "No asset files uploaded";
        return files.switchIfEmpty(Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, emptyMessage)))
            .flatMap(filePart -> {
                String fileName = ApiSupport.sanitizeFileName(filePart.filename(), "upload.bin");
                File target = ApiSupport.uniqueFile(targetDir, fileName);
                return filePart.transferTo(target.toPath()).thenReturn(target.getAbsoluteFile());
            })
            .collectList()
            .map(savedFiles -> facade.uploadedFiles(sessionId, savedFiles, libraries));
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
