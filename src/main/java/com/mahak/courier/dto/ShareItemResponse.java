package com.mahak.courier.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import com.mahak.courier.entity.FileType;

@Getter
@Setter
@AllArgsConstructor
public class ShareItemResponse {
    private Long itemId;          // used by the download proxy endpoint
    private String fileUrl;
    private String originalFilename;
    private FileType fileType;
    private long fileSizeBytes;
}
