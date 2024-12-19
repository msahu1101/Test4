package com.mgm.payments.processing.service.model.payload.router;

import com.mgm.payments.processing.service.model.Amex;
import com.mgm.payments.processing.service.model.GatewayResponse;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RouterTransactionResponse {

    private String authorizationCode;
    private String authSource;
    private GatewayResponse gatewayResponse;
    private String gatewayChainId;
    private String responseCode;
    private String retrievalReference;
    private String saleFlag;
    private Amex amex;  //amex
    private String deferredAuth;
    private String avsResult;
    private String avsValid;
}
