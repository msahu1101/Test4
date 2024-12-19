package com.mgm.payments.processing.service.exception;

import com.mgm.payments.processing.service.model.payload.pps.exception.PaymentExceptionResponse;
import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
public class PaymentProcessingException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final PaymentExceptionResponse exceptionResponse;
    private final HttpStatus httpStatus;

    public PaymentProcessingException(PaymentExceptionResponse exceptionResponse, HttpStatus httpStatus) {
        this.exceptionResponse = exceptionResponse;
        this.httpStatus = httpStatus;
    }
}
