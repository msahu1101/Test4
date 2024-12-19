package com.mgm.payments.processing.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HotelData {
    private String folioNumber;
    private String checkInDate;
    private String checkOutDate;
    private String roomRate;
    private String expectedDuration;
}