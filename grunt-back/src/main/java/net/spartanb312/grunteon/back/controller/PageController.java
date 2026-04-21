package net.spartanb312.grunteon.back.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Controller
public class PageController {

    @GetMapping({"/", "/login", "/login.html"})
    public ResponseEntity<Resource> loginPage() {
        return html("web/login.html");
    }

    @GetMapping("/index.html")
    public ResponseEntity<Resource> indexPage() {
        return html("web/index.html");
    }

    private ResponseEntity<Resource> html(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Web UI not found");
        }
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(resource);
    }
}
