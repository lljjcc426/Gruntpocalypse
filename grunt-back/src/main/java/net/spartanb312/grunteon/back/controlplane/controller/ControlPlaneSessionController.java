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

@RestController
public class ControlPlaneSessionController {

    private final ControlPlaneSessionFacade facade;

    public ControlPlaneSessionController(ControlPlaneSessionFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/api/control/sessions")
    public Map<String, Object> createSession(@RequestBody(required = false) Map<String, Object> request) {
        String profile = request == null ? null : stringValue(request.get("profile"));
        return facade.createSession(profile, false);
    }

    @GetMapping("/api/control/sessions/{sessionId}")
    public Map<String, Object> status(@PathVariable String sessionId) {
        return facade.status(sessionId);
    }

    @GetMapping("/api/control/sessions/{sessionId}/logs")
    public List<String> logs(@PathVariable String sessionId) {
        return facade.logs(sessionId);
    }

    @PostMapping(path = "/api/control/sessions/{sessionId}/artifacts/config", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> uploadConfig(
        @PathVariable String sessionId,
        @RequestPart("file") Mono<FilePart> filePartMono
    ) {
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
        @RequestPart("file") Mono<FilePart> filePartMono
    ) {
        File targetDir = facade.requireSession(sessionId).getInputDir();
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
        @RequestPart("files") Flux<FilePart> files
    ) {
        return uploadFiles(sessionId, files, true);
    }

    @PostMapping(path = "/api/control/sessions/{sessionId}/artifacts/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> uploadAssets(
        @PathVariable String sessionId,
        @RequestPart("files") Flux<FilePart> files
    ) {
        return uploadFiles(sessionId, files, false);
    }

    @PostMapping("/api/control/sessions/{sessionId}/actions/obfuscate")
    public Map<String, Object> obfuscate(@PathVariable String sessionId) {
        return facade.obfuscate(sessionId);
    }

    @GetMapping("/api/control/sessions/{sessionId}/artifacts/output")
    public ResponseEntity<Resource> download(@PathVariable String sessionId) {
        return facade.download(sessionId);
    }

    @GetMapping("/api/control/sessions/{sessionId}/project/meta")
    public Map<String, Object> projectMeta(@PathVariable String sessionId, @RequestParam("scope") String scopeValue) {
        return facade.projectMeta(sessionId, scopeValue);
    }

    @GetMapping("/api/control/sessions/{sessionId}/project/tree")
    public Map<String, Object> projectTree(@PathVariable String sessionId, @RequestParam("scope") String scopeValue) {
        return facade.projectTree(sessionId, scopeValue);
    }

    @GetMapping("/api/control/sessions/{sessionId}/project/source")
    public Map<String, Object> projectSource(
        @PathVariable String sessionId,
        @RequestParam("scope") String scopeValue,
        @RequestParam("class") String className
    ) {
        return facade.projectSource(sessionId, scopeValue, className);
    }

    private Mono<Map<String, Object>> uploadFiles(String sessionId, Flux<FilePart> files, boolean libraries) {
        File targetDir = libraries ? facade.requireSession(sessionId).getLibrariesDir() : facade.requireSession(sessionId).getAssetsDir();
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
