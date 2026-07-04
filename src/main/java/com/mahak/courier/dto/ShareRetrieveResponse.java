package com.mahak.courier.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ShareRetrieveResponse {
    private String code;
    private List<ShareItemResponse> items;
    private boolean wasSelfDestructed;
    private boolean requiresPassword;
}
