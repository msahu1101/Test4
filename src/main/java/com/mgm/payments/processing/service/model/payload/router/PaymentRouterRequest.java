package com.mgm.payments.processing.service.model.payload.router;

import com.mgm.payments.processing.service.enums.RouterFunction;
import com.mgm.payments.processing.service.model.Amount;
import com.mgm.payments.processing.service.model.HotelData;
import com.mgm.payments.processing.service.model.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentRouterRequest {

    private String clientReferenceNumber;
    private String gatewayChainId;
    private RouterFunction routerFunction;
    private String sessionId;
    private String mgmId;
    private String gatewayId;
    private String gatewayToken;
    private String authType;
    private List<Amount> amount;
    private String source;
    private Payment payment;
    private HotelData hotelData;
    private String merchantReferenceCode;
    private List<Map<String, Object>> additionalAttributes;
}
