package com.mgm.payments.processing.service.model.payload.router;

import com.mgm.payments.processing.service.enums.CardPresent;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class RouterCardResponse {

    private String tenderDisplay;
    private String gatewayId;
    private Integer expireMonth;
    private Integer expireYear;
    private CardPresent present;
    private String gatewayToken;
    private String issuerType;
    private String securityCodeResult;
    private String securityCodeValid;
}
