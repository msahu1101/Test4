package com.mgm.payments.processing.service.model.payload.clientconfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClientConfigPayload {
    private String clientId;
    private List<ClientConfig> configDetails;
}
