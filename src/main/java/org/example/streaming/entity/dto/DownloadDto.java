package org.example.streaming.entity.dto;

import java.io.InputStream;

public record DownloadDto(
        InputStream resource,
        long size
) {}
