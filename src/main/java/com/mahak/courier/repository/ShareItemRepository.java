package com.mahak.courier.repository;

import com.mahak.courier.entity.ShareItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShareItemRepository extends JpaRepository<ShareItem, Long> {

    List<ShareItem> findByShareId(Long shareId);
}