package com.mgm.payments.processing.service.model.payload.pps.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.ZonedDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(Include.NON_NULL)
public class PaymentExceptionResponse implements Serializable {

    private String paymentId;
    private ZonedDateTime dateTime;
    private String errorCode;
    private String errorMessage;
    private String developerMessage;

    private String originError;


}