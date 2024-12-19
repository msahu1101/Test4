package com.mgm.payments.processing.service.model.payload.router;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class PaymentRouterResponse {

    private List<RouterResult> results;
}
