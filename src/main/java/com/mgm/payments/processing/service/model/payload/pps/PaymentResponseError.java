package com.mgm.payments.processing.service.model.payload.pps;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
public class PaymentResponseError {

    private String code;
    private String severity;
    private String primaryCode;
    private String secondaryCode;
    private String shortText;
    private String longText;

}
