package com.mahak.courier.service;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
public class FileValidationService {

    private static final Logger logger = LoggerFactory.getLogger(FileValidationService.class);
    private final Tika tika = new Tika();

    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            // Images
            "image/jpeg", "image/jpg", "image/png", "image/gif",
            "image/webp", "image/bmp", "image/tiff",
            // Videos
            "video/mp4", "video/mpeg", "video/quicktime", "video/x-msvideo",
            "video/x-ms-wmv", "video/webm", "video/x-flv", "video/3gpp"
    );

    // S3 supports up to 5TB — we cap at 100MB per file for practical reasons
    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024; // 100 MB
    private static final int  MAX_FILES_PER_UPLOAD = 20;

    public void validateFiles(List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("No files provided");
        }
        if (files.size() > MAX_FILES_PER_UPLOAD) {
            throw new IllegalArgumentException(
                    "Too many files. Maximum " + MAX_FILES_PER_UPLOAD + " files allowed per upload");
        }

        long totalSize = 0;
        for (MultipartFile file : files) {
            validateSingleFile(file);
            totalSize += file.getSize();
        }

        long totalLimitBytes = MAX_FILE_SIZE_BYTES * MAX_FILES_PER_UPLOAD;
        if (totalSize > totalLimitBytes) {
            throw new IllegalArgumentException(
                    "Total upload size exceeds the 2 GB limit (100 MB × 20 files)");
        }
    }

    public void validateSingleFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty: " + file.getOriginalFilename());
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            long sizeMB = file.getSize() / (1024 * 1024);
            throw new IllegalArgumentException(
                    "File '" + file.getOriginalFilename() + "' is " + sizeMB + " MB. Maximum allowed is 100 MB.");
        }

        // Validate by actual file content (magic bytes), not the declared extension
        String detected = tika.detect(file.getInputStream());
        logger.info("MIME check — file: {}, detected: {}", file.getOriginalFilename(), detected);

        if (!ALLOWED_MIME_TYPES.contains(detected)) {
            throw new IllegalArgumentException(
                    "File type not allowed: " + file.getOriginalFilename() +
                    " (detected: " + detected + "). Only images and videos are accepted.");
        }
    }
}
