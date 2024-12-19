package com.mgm.payments.processing.service.model.payload.session;

import com.mgm.payments.processing.service.model.BillingAddress;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Item {
    private String id;
    private String confirmationNumber;
    private String itemId;
    private String itemType;
    private String itemName;
    private String seasonId;
    private String propertyId;
    private String propertyName;
    private String description;
    private int quantity;
    private int numberOfGuests;
    private List<Seat> seat;
    private PriceModifier delivery;
    private Duration duration;
    private BillingAddress locationAddress;
    private SessionAmount amount;
    private List<KeyValueAttributes> additionalFraudParams;


}
