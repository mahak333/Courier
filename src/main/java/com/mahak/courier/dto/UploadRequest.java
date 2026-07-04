package com.mahak.courier.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UploadRequest {
    private boolean selfDestruct;
    private Integer expiryHours; // null = use default (24h)
    private String password;     // optional password protection
}
