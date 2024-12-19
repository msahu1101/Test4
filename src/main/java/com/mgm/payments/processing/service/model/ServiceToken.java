package com.mgm.payments.processing.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceToken implements Serializable {

    private static final long serialVersionUID = 1L;
    private String token_type;
    private int expires_in;
    private String access_token;
    private String scope;

}
