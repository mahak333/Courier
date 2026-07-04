package com.mahak.courier.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

@Service
public class S3Service {

    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.bucket-name}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

    public S3Service(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    /**
     * Uploads a file to S3 and returns its public URL.
     * Files are streamed directly — no full file loaded into memory.
     *
     * @param file         the multipart file to upload
     * @param contentType  MIME type (e.g. "image/jpeg", "video/mp4")
     * @return             the public HTTPS URL of the uploaded object
     */
    public String uploadFile(MultipartFile file, String contentType) throws IOException {
        String key = buildS3Key(file.getOriginalFilename(), contentType);

        logger.info("Uploading to S3: key={}, size={}MB, type={}",
                key, String.format("%.1f", file.getSize() / (1024.0 * 1024.0)), contentType);

        long startMs = System.currentTimeMillis();

        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(inputStream, file.getSize()));
        }

        logger.info("S3 upload complete in {}ms: {}", System.currentTimeMillis() - startMs, key);

        return buildPublicUrl(key);
    }

    /**
     * Deletes an object from S3 by its key (publicId stored in DB).
     */
    public void deleteFile(String s3Key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build());
            logger.info("Deleted from S3: {}", s3Key);
        } catch (Exception e) {
            // Log but don't rethrow — a missing file shouldn't crash cleanup
            logger.warn("Failed to delete S3 object {}: {}", s3Key, e.getMessage());
        }
    }

    /**
     * Streams an S3 object back to the caller as an InputStream.
     * Used by the download proxy endpoint so files are never loaded fully into memory.
     */
    public InputStream downloadObject(String s3Key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();
        return s3Client.getObject(getRequest);
    }
    public String generatePresignedUrl(String s3Key, Duration expiry) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .build())
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a unique S3 key like: courier/images/uuid-filename.jpg
     */
    private String buildS3Key(String originalFilename, String contentType) {
        String folder = contentType != null && contentType.startsWith("video") ? "videos" : "images";
        String uuid = UUID.randomUUID().toString();
        String safeName = sanitizeFilename(originalFilename);
        return "courier/" + folder + "/" + uuid + "-" + safeName;
    }

    /**
     * Returns a public HTTPS URL for the object.
     * Works when the bucket has public-read ACL or a bucket policy allowing GetObject.
     */
    private String buildPublicUrl(String key) {
        return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "file";
        // Replace spaces and special chars except dot and hyphen
        return filename.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
    }
}
