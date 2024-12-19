package com.mgm.payments.processing.service.model.payload.pps;

import com.mgm.payments.processing.service.enums.TransactionType;
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
@EqualsAndHashCode
public class GatewayResult {

    private String clientReferenceNumber;
    private String groupId;
    private TransactionType type;
    private String mgmId;
    private Card card;
    private String transactionStatus;
    private String transactionCode;
    private Transaction transaction;
    private ZonedDateTime dateTime;
    private List<Amount> amount;


}
