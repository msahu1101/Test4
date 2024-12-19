package com.mgm.payments.processing.service.model.payload.router;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class RouterResult {
    private RouterGatewayResult gatewayResult;
}
