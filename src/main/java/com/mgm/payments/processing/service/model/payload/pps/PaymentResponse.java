package com.mgm.payments.processing.service.model.payload.pps;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class PaymentResponse {

    private List<Result> results;
    private String statusCode;
    private String statusDesc;


}