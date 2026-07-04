package com.mahak.courier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shares", indexes = {
    @Index(name = "idx_code", columnList = "code"),
    @Index(name = "idx_status_expires", columnList = "status,expiresAt")
})
@Getter
@Setter
@NoArgsConstructor
public class Share {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 6)
    private String code;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Boolean selfDestruct = false;

    @Column(nullable = false)
    private Integer viewCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShareStatus status = ShareStatus.ACTIVE;

    @Column(length = 60) // bcrypt hash length
    private String passwordHash;

    @OneToMany(mappedBy = "share", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ShareItem> items = new ArrayList<>();
}