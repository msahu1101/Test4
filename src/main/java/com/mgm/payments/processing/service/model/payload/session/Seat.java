package com.mgm.payments.processing.service.model.payload.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Seat {
    private String row;
    private String section;
    private String seatNumber;
    private double price;
}
