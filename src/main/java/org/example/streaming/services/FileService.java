package org.example.streaming.services;

import org.example.streaming.entity.dto.DownloadDto;
import org.example.streaming.entity.dto.FilesDto;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileService {

    void upload(MultipartFile document);

    FilesDto listFiles();

    DownloadDto download(String fileIdentifier) throws IOException;
}
