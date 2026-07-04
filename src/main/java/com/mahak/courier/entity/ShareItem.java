package com.mahak.courier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "share_items")
@Getter
@Setter
@NoArgsConstructor
public class ShareItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "share_id", nullable = false)
    private Share share;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false, length = 1000)
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileType fileType;

    @Column(nullable = false)
    private Long fileSizeBytes;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @Column(nullable = false, length = 500)
    private String publicId;
}