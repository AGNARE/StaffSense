package com.staffsense.demo.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
@RequestMapping("/api/dataset")
public class DatasetController {

    @GetMapping("/training")
    public ResponseEntity<Resource> downloadTraining() {
        Path file = Paths.get("dataset_training.csv").toAbsolutePath().normalize();
        return serveFile(file);
    }

    @GetMapping("/test")
    public ResponseEntity<Resource> downloadTest() {
        Path file = Paths.get("dataset_test.csv").toAbsolutePath().normalize();
        return serveFile(file);
    }

    private ResponseEntity<Resource> serveFile(Path file) {
        if (!file.toFile().exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getFileName())
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }
}
