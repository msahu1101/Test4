package com.mgm.payments.processing.service.model.payload.clientconfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClientConfig {
    private String configName;
    private Object configValue;
}
