package com.mgm.payments.processing.service.model.payload.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PriceModifier {
    private String type;
    private String method;
    private String code;
    private String amount;
}
