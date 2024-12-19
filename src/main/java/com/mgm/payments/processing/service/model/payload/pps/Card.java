package com.mgm.payments.processing.service.model.payload.pps;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
public class Card {

    private String maskedCardNumber;
    private String gatewayId;
    private String mgmToken;
    private String issuerType;
    private String securityCodeResult;
    private String securityCodeValid;
}
