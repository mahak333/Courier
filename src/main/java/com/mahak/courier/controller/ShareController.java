package com.mahak.courier.controller;

import com.mahak.courier.dto.ShareResponse;
import com.mahak.courier.dto.ShareRetrieveResponse;
import com.mahak.courier.dto.UploadRequest;
import com.mahak.courier.service.QRCodeService;
import com.mahak.courier.service.RateLimitService;
import com.mahak.courier.service.ShareService;
import com.mahak.courier.service.ShareService.DownloadResult;
import com.google.zxing.WriterException;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/share")
public class ShareController {

    private final ShareService shareService;
    private final RateLimitService rateLimitService;
    private final QRCodeService qrCodeService;

    public ShareController(ShareService shareService,
                           RateLimitService rateLimitService,
                           QRCodeService qrCodeService) {
        this.shareService = shareService;
        this.rateLimitService = rateLimitService;
        this.qrCodeService = qrCodeService;
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<?> createShare(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "selfDestruct", defaultValue = "false") boolean selfDestruct,
            @RequestParam(value = "expiryHours", required = false) Integer expiryHours,
            @RequestParam(value = "password", required = false) String password,
            HttpServletRequest request
    ) throws IOException {

        String clientIp = getClientIp(request);
        Bucket bucket = rateLimitService.getUploadBucket(clientIp);

        if (!bucket.tryConsume(1)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Too many uploads");
            error.put("message", "Upload limit exceeded. Please try again later.");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
        }

        UploadRequest uploadRequest = new UploadRequest();
        uploadRequest.setSelfDestruct(selfDestruct);
        uploadRequest.setExpiryHours(expiryHours);
        uploadRequest.setPassword(password);

        ShareResponse response = shareService.createShare(files, uploadRequest);
        return ResponseEntity.ok(response);
    }

    // ── Retrieve ──────────────────────────────────────────────────────────────

    @GetMapping("/{code}")
    public ResponseEntity<ShareRetrieveResponse> retrieveShare(
            @PathVariable String code,
            @RequestParam(value = "password", required = false) String password,
            HttpServletRequest request
    ) {
        String clientIp = getClientIp(request);
        Bucket bucket = rateLimitService.getRetrieveBucket(clientIp);

        if (!bucket.tryConsume(1)) {
            throw new RuntimeException("Too many retrieve attempts. Please try again later.");
        }

        ShareRetrieveResponse response = shareService.retrieveShare(code, password);
        return ResponseEntity.ok(response);
    }

    // ── Download proxy ────────────────────────────────────────────────────────
    // The browser can't fetch S3 URLs as blobs (CORS blocks it).
    // Instead the frontend calls this endpoint; Spring fetches from S3
    // server-side and streams the bytes back with Content-Disposition: attachment
    // so the browser always saves the file instead of opening it.

    @GetMapping("/download/{itemId}")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable Long itemId
    ) {
        DownloadResult result = shareService.downloadItem(itemId);

        // RFC 5987 — encode filename so non-ASCII chars work correctly
        String encodedName = URLEncoder.encode(result.filename(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(result.contentType()));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + result.filename() + "\"; filename*=UTF-8''" + encodedName);
        if (result.sizeBytes() > 0) {
            headers.setContentLength(result.sizeBytes());
        }

        StreamingResponseBody body = outputStream -> {
            try (InputStream in = result.stream()) {
                in.transferTo(outputStream);
            }
        };

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    // ── QR code ───────────────────────────────────────────────────────────────

    @GetMapping("/{code}/qr")
    public ResponseEntity<byte[]> generateQRCode(
            @PathVariable String code,
            @RequestParam(value = "size", defaultValue = "300") int size
    ) throws WriterException, IOException {
        String shareUrl = "http://localhost:8080?code=" + code;
        byte[] qrCode = qrCodeService.generateQRCode(shareUrl, size, size);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(qrCode);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isEmpty()) return xri;
        return request.getRemoteAddr();
    }
}
