package com.mgm.payments.processing.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GatewayResponse {
    private String reasonCode;
    private String reasonDescription;
    private String reattemptPermission;
}
