package com.mahak.courier.service;

import com.mahak.courier.dto.*;
import com.mahak.courier.entity.*;
import com.mahak.courier.repository.ShareItemRepository;
import com.mahak.courier.repository.ShareRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class ShareService {

    private static final Logger logger = LoggerFactory.getLogger(ShareService.class);

    private final ShareRepository shareRepository;
    private final ShareItemRepository shareItemRepository;
    private final CodeGeneratorService codeGeneratorService;
    private final S3Service s3Service;
    private final FileValidationService fileValidationService;
    private final ExecutorService executorService;
    private final BCryptPasswordEncoder passwordEncoder;

    private static final int DEFAULT_EXPIRY_HOURS = 24;
    private static final int MAX_EXPIRY_HOURS = 168; // 7 days
    private static final int PARALLEL_UPLOAD_THREADS = 3;

    public ShareService(ShareRepository shareRepository,
                        ShareItemRepository shareItemRepository,
                        CodeGeneratorService codeGeneratorService,
                        S3Service s3Service,
                        FileValidationService fileValidationService) {
        this.shareRepository = shareRepository;
        this.shareItemRepository = shareItemRepository;
        this.codeGeneratorService = codeGeneratorService;
        this.s3Service = s3Service;
        this.fileValidationService = fileValidationService;
        this.executorService = Executors.newFixedThreadPool(PARALLEL_UPLOAD_THREADS);
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Transactional
    @CacheEvict(value = "shares", allEntries = true)
    public ShareResponse createShare(List<MultipartFile> files, UploadRequest request) throws IOException {
        logger.info("Starting share creation with {} file(s)", files.size());

        fileValidationService.validateFiles(files);

        Share share = new Share();
        share.setCode(codeGeneratorService.generateUniqueCode());
        share.setCreatedAt(LocalDateTime.now());

        int expiryHours = (request.getExpiryHours() != null)
                ? Math.min(request.getExpiryHours(), MAX_EXPIRY_HOURS)
                : DEFAULT_EXPIRY_HOURS;
        share.setExpiresAt(LocalDateTime.now().plusHours(expiryHours));
        share.setSelfDestruct(request.isSelfDestruct());
        share.setStatus(ShareStatus.ACTIVE);

        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            share.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        // Upload all files in parallel
        List<CompletableFuture<ShareItem>> futures = new ArrayList<>();
        for (MultipartFile file : files) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return processFileUpload(file, share);
                } catch (IOException e) {
                    logger.error("Failed to upload: {}", file.getOriginalFilename(), e);
                    throw new RuntimeException("Failed to upload: " + file.getOriginalFilename(), e);
                }
            }, executorService));
        }

        try {
            List<ShareItem> items = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
            share.getItems().addAll(items);
        } catch (Exception e) {
            logger.error("Error during parallel upload", e);
            throw new IOException("Failed to upload one or more files: " + e.getMessage(), e);
        }

        Share saved = shareRepository.save(share);
        logger.info("Share created — code: {}, files: {}", saved.getCode(), saved.getItems().size());

        return new ShareResponse(
                saved.getCode(),
                saved.getExpiresAt(),
                saved.getSelfDestruct(),
                saved.getItems().size()
        );
    }

    private ShareItem processFileUpload(MultipartFile file, Share share) throws IOException {
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        FileType fileType = contentType.startsWith("video") ? FileType.VIDEO : FileType.IMAGE;

        long start = System.currentTimeMillis();
        String fileUrl = s3Service.uploadFile(file, contentType);
        logger.info("Uploaded {} in {}ms", file.getOriginalFilename(), System.currentTimeMillis() - start);

        // S3 key is embedded in the URL — extract it as publicId for later deletion
        String s3Key = extractS3Key(fileUrl);

        ShareItem item = new ShareItem();
        item.setShare(share);
        item.setOriginalFilename(file.getOriginalFilename());
        item.setFileUrl(fileUrl);
        item.setPublicId(s3Key);   // stored so cleanup can delete from S3
        item.setFileType(fileType);
        item.setFileSizeBytes(file.getSize());
        item.setUploadedAt(LocalDateTime.now());
        return item;
    }

    @Transactional
    @Cacheable(value = "shares", key = "#code")
    public ShareRetrieveResponse retrieveShare(String code, String password) {
        Share share = shareRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Invalid or expired code"));

        if (share.getStatus() != ShareStatus.ACTIVE) {
            throw new RuntimeException("This share is no longer available");
        }

        if (share.getExpiresAt().isBefore(LocalDateTime.now())) {
            share.setStatus(ShareStatus.EXPIRED);
            shareRepository.save(share);
            throw new RuntimeException("This share has expired");
        }

        if (share.getPasswordHash() != null) {
            if (password == null || password.trim().isEmpty()) {
                return new ShareRetrieveResponse(share.getCode(), null, false, true);
            }
            if (!passwordEncoder.matches(password, share.getPasswordHash())) {
                throw new RuntimeException("Incorrect password");
            }
        }

        share.setViewCount(share.getViewCount() + 1);

        List<ShareItemResponse> itemResponses = share.getItems().stream()
                .map(item -> new ShareItemResponse(
                        item.getId(),
                        item.getFileUrl(),
                        item.getOriginalFilename(),
                        item.getFileType(),
                        item.getFileSizeBytes()
                ))
                .collect(Collectors.toList());

        boolean willDestruct = share.getSelfDestruct();
        if (willDestruct) {
            share.setStatus(ShareStatus.DESTROYED);
        }

        shareRepository.save(share);
        return new ShareRetrieveResponse(share.getCode(), itemResponses, willDestruct, false);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the S3 object key from a full S3 URL.
     * e.g. https://bucket.s3.region.amazonaws.com/courier/images/uuid-file.jpg
     *   -> courier/images/uuid-file.jpg
     */
    private String extractS3Key(String url) {
        try {
            // URL format: https://<bucket>.s3.<region>.amazonaws.com/<key>
            String host = url.split("/")[2];               // bucket.s3.region.amazonaws.com
            return url.substring(url.indexOf(host) + host.length() + 1); // strip protocol+host
        } catch (Exception e) {
            return url; // fallback — store full URL
        }
    }

    // -------------------------------------------------------------------------
    // Download support
    // -------------------------------------------------------------------------

    public record DownloadResult(InputStream stream, String filename, String contentType, long sizeBytes) {}

    /**
     * Fetches a single ShareItem from the database and streams it from S3.
     * The controller pipes this stream directly to the HTTP response.
     */
    public DownloadResult downloadItem(Long itemId) {
        ShareItem item = shareItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        // Guard: only serve files belonging to active (or destroyed-by-self-destruct) shares
        Share share = item.getShare();
        if (share.getStatus() == ShareStatus.EXPIRED) {
            throw new RuntimeException("This share has expired");
        }

        String contentType = item.getFileType() == FileType.VIDEO ? "video/mp4" : "image/jpeg";
        InputStream stream = s3Service.downloadObject(item.getPublicId());
        return new DownloadResult(stream, item.getOriginalFilename(), contentType, item.getFileSizeBytes());
    }
}
