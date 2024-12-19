package com.mgm.payments.processing.service.model.payload.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionAmount {
    private List<KeyValueAttributes> totalAmount;
    private List<KeyValueAttributes> itemizedCharges;
    private List<PriceModifier> taxesAndFees;
    private List<PriceModifier> discounts;
}
