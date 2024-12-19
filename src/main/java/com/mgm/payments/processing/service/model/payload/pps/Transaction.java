package com.mgm.payments.processing.service.model.payload.pps;

import com.mgm.payments.processing.service.model.GatewayResponse;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
public class Transaction {

    private String paymentId;
    private String authorizationCode;
    private GatewayResponse gatewayResponse;
    private String avsResult;
    private String avsValid;
    private String responseCode;


}
