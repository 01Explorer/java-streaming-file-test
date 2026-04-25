package org.example.streaming.services;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    void upload(MultipartFile document);
}
