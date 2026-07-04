package com.mahak.courier.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class ShareResponse {
    private String code;
    private LocalDateTime expiresAt;
    private boolean selfDestruct;
    private int itemCount;
}