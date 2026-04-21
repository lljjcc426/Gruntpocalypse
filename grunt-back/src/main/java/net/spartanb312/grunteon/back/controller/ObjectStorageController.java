package net.spartanb312.grunteon.back.controller;

import java.io.File;
import java.util.Map;
import net.spartanb312.grunteon.back.support.ApiSupport;
import net.spartanb312.grunteon.obfuscator.web.ObjectStorageService;
import net.spartanb312.grunteon.obfuscator.web.ObjectTicket;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
public class ObjectStorageController {

    private final ObjectStorageService objectStorageService;

    public ObjectStorageController(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    @PostMapping("/api/v1/artifacts/upload-url")
    public Map<String, Object> createUploadTicket(@RequestBody(required = false) Map<String, Object> request) {
        String fileName = request == null ? null : String.valueOf(request.get("fileName"));
        String kind = request == null ? null : String.valueOf(request.get("kind"));
        if ("null".equals(fileName)) {
            fileName = null;
        }
        if ("null".equals(kind)) {
            kind = null;
        }
        ObjectTicket ticket = objectStorageService.createUploadTicket(fileName, kind);
        Map<String, Object> result = ApiSupport.ok();
        result.put("objectKey", ticket.getObjectKey());
        result.put("method", ticket.getMethod());
        result.put("uploadUrl", ticket.getUrl());
        result.put("expiresAt", ticket.getExpiresAt());
        return result;
    }

    @PutMapping(path = "/api/v1/storage/{*path}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Map<String, Object> putObject(@PathVariable String path, @RequestBody byte[] bytes) {
        String objectKey = normalizePath(path);
        objectStorageService.putObject(objectKey, bytes);
        Map<String, Object> result = ApiSupport.ok();
        result.put("objectKey", objectKey);
        result.put("size", bytes.length);
        return result;
    }

    @GetMapping("/api/v1/storage/{*path}")
    public ResponseEntity<Resource> getObject(@PathVariable String path) {
        String objectKey = normalizePath(path);
        File file = objectStorageService.getObject(objectKey);
        return ResponseEntity.ok().body(new FileSystemResource(file));
    }

    private String normalizePath(String path) {
        String objectKey = path;
        if (objectKey.startsWith("/")) {
            objectKey = objectKey.substring(1);
        }
        if (objectKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid object key");
        }
        return objectKey;
    }
}
