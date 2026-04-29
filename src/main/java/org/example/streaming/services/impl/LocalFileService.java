package org.example.streaming.services.impl;

import org.example.streaming.entity.dto.DownloadDto;
import org.example.streaming.entity.dto.FilesDto;
import org.example.streaming.exceptions.DownloadException;
import org.example.streaming.exceptions.UploadException;
import org.example.streaming.services.FileService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class LocalFileService implements FileService {

    private static final String USER_DIR = "user.dir";

    @Override
    public void upload(MultipartFile document) {
        if (document.isEmpty()) {
            throw new UploadException("File is empty or broken");
        }

        Path path = Paths.get(getDestinationFolder());
        if (!Files.isDirectory(path)){
            createFolder(path);
        }

        try {
            OutputStream out = new BufferedOutputStream(new FileOutputStream(buildPath(document.getOriginalFilename())), 16384);
            document.getInputStream().transferTo(out);
        } catch (FileNotFoundException e) {
            throw new UploadException("File is empty or broken");
        } catch (IOException e) {
            throw new UploadException("Failed to write file");
        }
    }

    @Override
    public FilesDto listFiles() {
        Path path = Paths.get(getDestinationFolder());
        if (!Files.isDirectory(path)){
            return null;
        }
        List<String> fileNames = new ArrayList<>();
        try (Stream<Path> paths = Files.list(path)){
            paths.forEach(file -> fileNames.add(file.getFileName().toString()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new FilesDto(fileNames);
    }

    @Override
    public DownloadDto download(String fileIdentifier) {
        Path path = Paths.get(getDestinationFolder(), fileIdentifier);
        if (!Files.exists(path)){
            throw new DownloadException("File not found");
        }

        try {
            InputStream stream = Files.newInputStream(path);
            long size = Files.size(path);
            return new DownloadDto(stream, size);
        } catch (IOException e) {
            throw new DownloadException("Internal server error while downloading file");
        }


    }

    private void createFolder(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new UploadException("Failed to create destination directories");
        }
    }

    private String buildPath(@Nullable String originalFilename) {
        if (originalFilename == null){
            originalFilename = "tmp_" + Instant.now().toString();
        }
        return getDestinationFolder() + "/" + originalFilename;
    }

    private String getDestinationFolder(){
        String dir = System.getProperty(USER_DIR);
        return dir + "/uploads";
    }
}
