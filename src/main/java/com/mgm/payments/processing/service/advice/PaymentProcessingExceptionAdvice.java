package com.mgm.payments.processing.service.advice;

import brave.Span;
import brave.Tracer;
import com.mgm.payments.processing.service.enums.ApiErrorCode;
import com.mgm.payments.processing.service.enums.StatusResult;
import com.mgm.payments.processing.service.exception.PaymentProcessingException;
import com.mgm.payments.processing.service.model.payload.pps.exception.PaymentExceptionResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.*;

@Slf4j
@ControllerAdvice
@ResponseBody
@RestControllerAdvice
public class PaymentProcessingExceptionAdvice {


    Tracer tracer;

    @Autowired
    public PaymentProcessingExceptionAdvice(Tracer tracer) {
        this.tracer = tracer;
    }

    private String getTraceId() {
        Span span = tracer.currentSpan();
        if (span != null) {
            return span.context().traceIdString();
        }
        return null;
    }

    private String getSpanId() {
        Span span = tracer.currentSpan();
        if (span != null) {
            return span.context().spanIdString();
        }
        return null;
    }

    /**
     * Handle Payment Processing Exception
     *
     * @param paymentProcessingException- exception thrown for clientError/ServerError/Invalid input
     * @return PaymentExceptionResponse- Response Entity of ExceptionResponse DTO of exceptionDetails and httpStatus
     */
    @ExceptionHandler(PaymentProcessingException.class)
    public final ResponseEntity<PaymentExceptionResponse> handlePaymentProcessingException(
            PaymentProcessingException paymentProcessingException, HttpServletRequest request) {
        PaymentExceptionResponse exceptionResponse = paymentProcessingException.getExceptionResponse();
        log.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "handlePaymentProcessingException", ADVICE_CLASS_NAME,
                request.getHeader(MGM_SOURCE), request.getHeader(CHANNEL), request.getHeader(JOURNEY_ID), request.getHeader(CORRELATION_ID),
                request.getHeader(TRANSACTION_ID), request.getHeader(CLIENT_ID), "", getSpanId(), getTraceId(),
                StatusResult.E.name(), StatusResult.E.getResult(), "", exceptionResponse);
        return new ResponseEntity<>(exceptionResponse, paymentProcessingException.getHttpStatus());
    }

    /**
     * Handle Bad Request Exception
     *
     * @param ex- MethodArgumentNotValidException thrown when Invalid Input Data
     * @return PaymentExceptionResponse- Response Entity of ExceptionResponse DTO of exceptionDetails and httpStatus : 400
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<PaymentExceptionResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        StringBuilder developerMessage = new StringBuilder();
        StringBuilder errorMessage = new StringBuilder();
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        fieldErrors.forEach(field ->{
            developerMessage.append(field.getObjectName()).append(" : ")
                    .append(field.getField()).append(" ::: ").append(field.getDefaultMessage()).append("!! ");
            errorMessage.append(field.getObjectName()).append(" : ")
                    .append(field.getField())
                    .append("; ");
                }

        );
        PaymentExceptionResponse errorResponse = PaymentExceptionResponse.builder()
                .dateTime(ZonedDateTime.now())
                .errorMessage(ApiErrorCode.FIELD_VALIDATION_FAILED_MESSAGE.getDescription() + errorMessage)
                .errorCode(ApiErrorCode.FIELD_VALIDATION_FAILED_MESSAGE.getCode())
                .developerMessage(developerMessage.toString())
                .build();
        log.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "handleMethodArgumentNotValidException", ADVICE_CLASS_NAME,
                request.getHeader(MGM_SOURCE), request.getHeader(CHANNEL), request.getHeader(JOURNEY_ID), request.getHeader(CORRELATION_ID),
                request.getHeader(TRANSACTION_ID), request.getHeader(CLIENT_ID), "", getSpanId(), getTraceId(),
                StatusResult.E.name(), StatusResult.E.getResult(), "", errorResponse);
        return new ResponseEntity<>(errorResponse,
                new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<PaymentExceptionResponse> handleValidationErrors(ConstraintViolationException ex, HttpServletRequest request) {
        StringBuilder developerMessage = new StringBuilder();
        StringBuilder errorMessage = new StringBuilder();
        Set<ConstraintViolation<?>> fieldErrors = ex.getConstraintViolations();
        fieldErrors.forEach(field ->{
                developerMessage.append(field.getMessage()).append("!! ");
                errorMessage.append(field).append("; ");
        });
        PaymentExceptionResponse errorResponse = PaymentExceptionResponse.builder()
                .dateTime(ZonedDateTime.now())
                .errorMessage(ApiErrorCode.FIELD_VALIDATION_FAILED_MESSAGE.getDescription() + errorMessage)
                .errorCode(ApiErrorCode.FIELD_VALIDATION_FAILED_MESSAGE.getCode())
                .developerMessage(developerMessage.toString())
                .build();
        log.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "handleConstraintViolationException", ADVICE_CLASS_NAME,
                request.getHeader(MGM_SOURCE), request.getHeader(CHANNEL), request.getHeader(JOURNEY_ID), request.getHeader(CORRELATION_ID),
                request.getHeader(TRANSACTION_ID), request.getHeader(CLIENT_ID), "", getSpanId(), getTraceId(),
                StatusResult.E.name(), StatusResult.E.getResult(), "", errorResponse);
        return new ResponseEntity<>(errorResponse,
                new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<PaymentExceptionResponse> handleValidationErrors(MissingRequestHeaderException ex, HttpServletRequest request) {
        String fieldError = ex.getMessage();
        PaymentExceptionResponse errorResponse = PaymentExceptionResponse.builder()
                .dateTime(ZonedDateTime.now())
                .developerMessage(ApiErrorCode.FIELD_VALIDATION_FAILED_MESSAGE.getDescription() +fieldError )
                .errorCode(ApiErrorCode.FIELD_VALIDATION_FAILED_MESSAGE.getCode())
                .errorMessage(fieldError)
                .build();
        log.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "handleMissingRequestHeaderException", ADVICE_CLASS_NAME,
                request.getHeader(MGM_SOURCE), request.getHeader(CHANNEL), request.getHeader(JOURNEY_ID), request.getHeader(CORRELATION_ID),
                request.getHeader(TRANSACTION_ID), request.getHeader(CLIENT_ID), "", getSpanId(), getTraceId(),
                StatusResult.E.name(), StatusResult.E.getResult(), "", errorResponse);
        return new ResponseEntity<>(errorResponse,
                new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }



    @ExceptionHandler(Exception.class)
    public ResponseEntity<PaymentExceptionResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        PaymentExceptionResponse errorResponse = PaymentExceptionResponse.builder()
                .dateTime(ZonedDateTime.now())
                .errorCode(ApiErrorCode.INTERNAL_SERVER_ERROR.getCode())
                .errorMessage(ApiErrorCode.INTERNAL_SERVER_ERROR.getDescription())
                .developerMessage(ex != null ? ex.toString() : "Unexpected Exception Occurred")
                .build();
        log.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "handleGenericException", ADVICE_CLASS_NAME,
                request.getHeader(MGM_SOURCE), request.getHeader(CHANNEL), request.getHeader(JOURNEY_ID), request.getHeader(CORRELATION_ID),
                request.getHeader(TRANSACTION_ID), request.getHeader(CLIENT_ID), "", getSpanId(), getTraceId(),
                StatusResult.E.name(), StatusResult.E.getResult(), "", errorResponse);
        return new ResponseEntity<>(errorResponse,
                new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<PaymentExceptionResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException  ex, HttpServletRequest request) {
        log.error("Advice : handleHttpMessageNotReadableException caught and Started :: exception :", ex);
        PaymentExceptionResponse errorResponse = PaymentExceptionResponse.builder()
                .dateTime(ZonedDateTime.now())
                .developerMessage("Provide value to parameters as per the API Contract !!")
                .errorCode(ApiErrorCode.INVALID_DATA_PASSED.getCode())
                .errorMessage(ApiErrorCode.INVALID_DATA_PASSED.getDescription())
                .originError(StringUtils.substringBefore(ex.getMessage(), "; nested exception is"))
                .build();
        log.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "handleHttpMessageNotReadableException", ADVICE_CLASS_NAME,
                request.getHeader(MGM_SOURCE), request.getHeader(CHANNEL), request.getHeader(JOURNEY_ID), request.getHeader(CORRELATION_ID),
                request.getHeader(TRANSACTION_ID), request.getHeader(CLIENT_ID), "", getSpanId(), getTraceId(),
                StatusResult.E.name(), StatusResult.E.getResult(), "", errorResponse);
        return new ResponseEntity<>(errorResponse,
                new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

}
