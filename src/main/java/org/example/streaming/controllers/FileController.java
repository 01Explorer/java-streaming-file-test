package org.example.streaming.controllers;

import org.example.streaming.entity.dto.DownloadDto;
import org.example.streaming.entity.dto.FilesDto;
import org.example.streaming.services.FileService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFile(@RequestPart MultipartFile document){
        fileService.upload(document);
        return ResponseEntity.ok("Success");
    }

    @GetMapping
    public ResponseEntity<FilesDto> listFiles(){
        return ResponseEntity.ok(fileService.listFiles());
    }

    @GetMapping("/{fileIdentifier}")
    public ResponseEntity<Resource> download(@PathVariable String fileIdentifier) throws IOException {
        DownloadDto download = fileService.download(fileIdentifier);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename\"" + fileIdentifier + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(download.size())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(download.resource()));
    }
}
