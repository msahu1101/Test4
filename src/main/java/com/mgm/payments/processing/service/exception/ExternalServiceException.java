package com.mgm.payments.processing.service.exception;

import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
public class ExternalServiceException extends RuntimeException {
    private final String routerError;
    private final HttpStatus statusCode;
    public ExternalServiceException(String error, HttpStatus status){
        this.routerError = error;
        this.statusCode = status;
    }
    private static final long serialVersionUID = 1L;

}