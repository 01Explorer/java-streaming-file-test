package org.example.streaming.services.impl;

import org.example.streaming.exceptions.UploadException;
import org.example.streaming.services.FileService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

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
            OutputStream out = new FileOutputStream(buildPath(document.getOriginalFilename()));
            document.getInputStream().transferTo(out);
        } catch (FileNotFoundException e) {
            throw new UploadException("File is empty or broken");
        } catch (IOException e) {
            throw new UploadException("Failed to write file");
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
