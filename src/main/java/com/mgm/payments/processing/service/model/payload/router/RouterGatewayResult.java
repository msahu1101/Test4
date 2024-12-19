package com.mgm.payments.processing.service.model.payload.router;

import com.mgm.payments.processing.service.enums.RouterFunction;
import com.mgm.payments.processing.service.model.Amount;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class RouterGatewayResult {

    private String clientReferenceNumber;
    private String mgmId;
    private RouterFunction routerFunction;

    private RouterCardResponse card;
    private RouterTransactionResponse transaction;

    private ZonedDateTime dateTime;
    private List<Amount> amount;
    private String server;
}
