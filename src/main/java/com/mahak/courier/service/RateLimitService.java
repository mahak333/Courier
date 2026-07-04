package com.mahak.courier.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<String, Bucket> uploadBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> retrieveBuckets = new ConcurrentHashMap<>();

    // Upload: 5 requests per hour per IP
    public Bucket getUploadBucket(String key) {
        return uploadBuckets.computeIfAbsent(key, k -> createUploadBucket());
    }

    // Retrieve: 20 requests per minute per IP (prevents brute force)
    public Bucket getRetrieveBucket(String key) {
        return retrieveBuckets.computeIfAbsent(key, k -> createRetrieveBucket());
    }

    private Bucket createUploadBucket() {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofHours(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private Bucket createRetrieveBucket() {
        Bandwidth limit = Bandwidth.classic(20, Refill.intervally(20, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
