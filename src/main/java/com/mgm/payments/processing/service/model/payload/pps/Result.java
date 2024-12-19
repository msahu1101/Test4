package com.mgm.payments.processing.service.model.payload.pps;

import lombok.*;

@Data
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class Result {
    private GatewayResult gatewayResult;
}