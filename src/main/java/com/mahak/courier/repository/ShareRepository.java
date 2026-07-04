package com.mahak.courier.repository;

import com.mahak.courier.entity.Share;
import com.mahak.courier.entity.ShareStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShareRepository extends JpaRepository<Share, Long> {

    boolean existsByCode(String code);

    Optional<Share> findByCode(String code);

    List<Share> findByStatusAndExpiresAtBefore(ShareStatus status, LocalDateTime dateTime);
}