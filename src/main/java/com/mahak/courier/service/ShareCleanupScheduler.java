package com.mahak.courier.service;

import com.mahak.courier.entity.Share;
import com.mahak.courier.entity.ShareStatus;
import com.mahak.courier.repository.ShareRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ShareCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ShareCleanupScheduler.class);

    private final ShareRepository shareRepository;
    private final S3Service s3Service;

    public ShareCleanupScheduler(ShareRepository shareRepository, S3Service s3Service) {
        this.shareRepository = shareRepository;
        this.s3Service = s3Service;
    }

    /**
     * Runs every hour. Finds all active shares past their expiry time,
     * deletes the actual files from S3, then marks the share as EXPIRED.
     */
    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void expireOldShares() {
        List<Share> expired = shareRepository.findByStatusAndExpiresAtBefore(
                ShareStatus.ACTIVE, LocalDateTime.now()
        );

        if (expired.isEmpty()) return;

        for (Share share : expired) {
            share.getItems().forEach(item -> {
                logger.info("Deleting S3 object for expired share {}: {}", share.getCode(), item.getPublicId());
                s3Service.deleteFile(item.getPublicId());
            });
            share.setStatus(ShareStatus.EXPIRED);
        }

        shareRepository.saveAll(expired);
        logger.info("Expired and cleaned up {} share(s)", expired.size());
    }
}
