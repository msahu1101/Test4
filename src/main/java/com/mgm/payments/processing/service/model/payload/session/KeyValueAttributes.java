package com.mgm.payments.processing.service.model.payload.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KeyValueAttributes {
    private String name;
    private String value;
}
