package com.mgm.payments.processing.service.controller;

import brave.Span;
import brave.Tracer;
import com.mgm.payments.processing.service.enums.RouterResponseCode;
import com.mgm.payments.processing.service.enums.StatusResult;
import com.mgm.payments.processing.service.model.HeadersDTO;
import com.mgm.payments.processing.service.model.User;
import com.mgm.payments.processing.service.model.payload.pps.PaymentRequest;
import com.mgm.payments.processing.service.model.payload.pps.PaymentResponse;
import com.mgm.payments.processing.service.model.payload.pps.exception.PaymentExceptionResponse;
import com.mgm.payments.processing.service.service.PaymentProcessingService;
import com.mgm.payments.processing.service.util.LogMaskingConverter;
import com.mgm.payments.processing.service.util.PaymentProcessingUtil;
import com.mgm.payments.processing.service.validation.group.AuthorizeGroup;
import com.mgm.payments.processing.service.validation.group.CaptureGroup;
import com.mgm.payments.processing.service.validation.group.RefundGroup;
import com.mgm.payments.processing.service.validation.group.VoidGroup;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StopWatch;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.validation.Valid;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.*;

@RestController
@RequestMapping(PPS_API_CONTEXT_PATH)
@Slf4j
@Validated
public class PaymentProcessingController {

    PaymentProcessingService paymentProcessingService;
    Tracer tracer;

    public PaymentProcessingController(PaymentProcessingService paymentProcessingService, Tracer tracer) {

        this.paymentProcessingService = paymentProcessingService;
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

    @GetMapping("/health")
    public String getHealth() {
        return "ok";
    }

    /**
     * @param authRequest-      PaymentRequest
     * @param mgmSource-        RequestHeader
     * @param mgmJourneyId-     RequestHeader
     * @param mgmCorrelationId- RequestHeader
     * @param mgmTransactionId- RequestHeader
     * @param mgmClientId-      RequestHeader
     * @param mgmChannel-       RequestHeader
     * @param jwtToken-         RequestHeader Jwt Token
     * @param userAgent-        RequestHeader
     * @param user-             RequestAttribute
     * @return PaymentResponse
     */
    @PostMapping(value = "/auth", consumes = "application/json", produces = "application/json")
    @ApiResponse(
            responseCode = "200",
            description = "Authorize the request",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaymentResponse.class)
            ))
    @ApiResponse(
            responseCode = "400",
            description = "Field Validation Failed",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaymentExceptionResponse.class)
            ))
    @ApiResponse(
            responseCode = "412",
            description = "Pre Condition Failed",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaymentExceptionResponse.class)
            ))
    @ApiResponse(
            responseCode = "500",
            description = "Internal Server Error",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaymentExceptionResponse.class)
            ))
    public Mono<ResponseEntity<PaymentResponse>> authorize(@Validated({AuthorizeGroup.class}) @Valid @RequestBody PaymentRequest authRequest,
                                                           @RequestHeader(value = "x-mgm-source", required = true) String mgmSource,
                                                           @RequestHeader(value = "x-mgm-journey-id", required = true) String mgmJourneyId,
                                                           @RequestHeader(value = "x-mgm-correlation-id", required = true) String mgmCorrelationId,
                                                           @RequestHeader(value = "x-mgm-transaction-id", required = true) String mgmTransactionId,
                                                           @RequestHeader(value = "x-mgm-client-id", required = true) String mgmClientId,
                                                           @RequestHeader(value = "x-mgm-channel", required = true) String mgmChannel,
                                                           @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = true) String jwtToken,
                                                           @RequestHeader(value = "user-agent", required = true) String userAgent,
                                                           @Parameter(hidden = true) @RequestAttribute("user") User user) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String maskedPayload = LogMaskingConverter.mask(authRequest);
        String mgmId = authRequest.getMgmId() != null && !authRequest.getMgmId().isBlank() ? authRequest.getMgmId() : " ";
        String headerParams = PaymentProcessingUtil.concatenateWithComma(mgmSource, mgmChannel, mgmJourneyId, mgmCorrelationId,
                mgmTransactionId, mgmClientId, mgmId);
        String spanTrace = PaymentProcessingUtil.concatenateWithComma(getSpanId(), getTraceId());
        String operationClass = PaymentProcessingUtil.concatenateWithComma(AUTHORIZE_OPERATION, CONTROLLER_CLASS_NAME);
        PaymentProcessingUtil.publishRequestLog(PPS_REQUEST_LOG_FORMAT, operationClass, headerParams, spanTrace, null, null, maskedPayload);

        HeadersDTO headersDTO = new HeadersDTO(mgmSource, mgmJourneyId, mgmCorrelationId, mgmTransactionId, mgmChannel, jwtToken,
                mgmClientId, userAgent);
        return paymentProcessingService.authorize(authRequest, user, headersDTO).map(paymentResponse ->
        {
            stopWatch.stop();
            String status = StatusResult.F.name();
            String result = StatusResult.F.getResult();
            String responseCode = paymentResponse.getResults().get(0).getGatewayResult().getTransaction().getResponseCode();
            if(responseCode.equals(RouterResponseCode.A.toString())){
                status = StatusResult.S.name();
                result = StatusResult.S.getResult();
            }
            String maskedResponse = LogMaskingConverter.mask(paymentResponse);
            String statusResult = PaymentProcessingUtil.concatenateWithComma(status, result);
            PaymentProcessingUtil.publishRequestLog(PPS_RESPONSE_LOG_FORMAT, operationClass, headerParams, spanTrace, statusResult, stopWatch.getTotalTimeMillis(), maskedResponse);
            return (new ResponseEntity<>(paymentResponse, HttpStatus.OK));
        });
    }

    /**
     * @param captureRequest    - Capture Payment Request
     * @param mgmSource-        RequestHeader
     * @param mgmJourneyId-     RequestHeader JourneyId
     * @param mgmCorrelationId- RequestHeader
     * @param mgmTransactionId- RequestHeader
     * @param mgmClientId-      RequestHeader
     * @param mgmChannel-       RequestHeader
     * @param jwtToken-         RequestHeader Jwt Token
     * @param userAgent-        RequestHeader
     * @param user-             RequestAttribute
     * @return CapturePaymentResponse
     */
    @PostMapping(value = "/capture", consumes = "application/json", produces = "application/json")
    @ApiResponse(
            responseCode = "200",
            description = "Capture the Authorized request",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaymentResponse.class)
            ))
    @ApiResponse(
            responseCode = "400",
            description = "Field Validation Failed",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaymentExceptionResponse.class)
            ))
    @ApiResponse(
            responseCode = "412",
            description = "Pre Condition Failed",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaymentExceptionResponse.class)
            ))
    @ApiResponse(
            responseCode = "500",
            description = "Internal Server Error",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaymentExceptionResponse.class)
            ))
    public Mono<ResponseEntity<PaymentResponse>> capture(@Validated({CaptureGroup.class}) @Valid @RequestBody PaymentRequest captureRequest,
                                                         @RequestHeader(value = "x-mgm-source", required = true) String mgmSource,
                                                         @RequestHeader(value = "x-mgm-journey-id", required = true) String mgmJourneyId,
                                                         @RequestHeader(value = "x-mgm-correlation-id", required = true) String mgmCorrelationId,
                                                         @RequestHeader(value = "x-mgm-transaction-id", required = true) String mgmTransactionId,
                                                         @RequestHeader(value = "x-mgm-client-id", required = true) String mgmClientId,
                                                         @RequestHeader(value = "x-mgm-channel", required = true) String mgmChannel,
                                                         @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = true) String jwtToken,
                                                         @RequestHeader(value = "user-agent", required = true) String userAgent,
                                                         @Parameter(hidden = true) @RequestAttribute("user") User user) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String maskedRequest = LogMaskingConverter.mask(captureRequest);
        String mgmId = captureRequest.getMgmId() != null && !captureRequest.getMgmId().isBlank() ? captureRequest.getMgmId() : " ";
        String headerParams = PaymentProcessingUtil.concatenateWithComma(mgmSource, mgmChannel, mgmJourneyId, mgmCorrelationId,
                mgmTransactionId, mgmClientId, mgmId);
        String spanTrace = PaymentProcessingUtil.concatenateWithComma(getSpanId(), getTraceId());
        String operationClass = PaymentProcessingUtil.concatenateWithComma(CAPTURE_OPERATION, CONTROLLER_CLASS_NAME);
        PaymentProcessingUtil.publishRequestLog(PPS_REQUEST_LOG_FORMAT, operationClass, headerParams, spanTrace, null, null, maskedRequest);
        HeadersDTO headersDTO = new HeadersDTO(mgmSource, mgmJourneyId, mgmCorrelationId, mgmTransactionId, mgmChannel, jwtToken,
                mgmClientId, userAgent);
        return paymentProcessingService.capture(captureRequest, user, headersDTO).map(paymentResponse ->
        {
            stopWatch.stop();
            String status = StatusResult.F.name();
            String result = StatusResult.F.getResult();
            String responseCode = paymentResponse.getResults().get(0).getGatewayResult().getTransaction().getResponseCode();
            if(responseCode.equals(RouterResponseCode.A.toString())){
                status = StatusResult.S.name();
                result = StatusResult.S.getResult();
            }
            String maskedResponse = LogMaskingConverter.mask(paymentResponse);
            String statusResult = PaymentProcessingUtil.concatenateWithComma(status, result);
            PaymentProcessingUtil.publishRequestLog(PPS_RESPONSE_LOG_FORMAT, operationClass, headerParams, spanTrace, statusResult, stopWatch.getTotalTimeMillis(), maskedResponse);
            return new ResponseEntity<>(paymentResponse, HttpStatus.OK);
        });

    }

    @PostMapping(value = "/refund", consumes = "application/json", produces = "application/json")
    @ApiResponse(
            responseCode = "200",
            description = "Refund request",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaymentResponse.class)
            ))
    @ApiResponse(
            responseCode = "400",
            description = "Field Validation Failed",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaymentExceptionResponse.class)
            ))
    @ApiResponse(
            responseCode = "412",
            description = "Pre Condition Failed",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaymentExceptionResponse.class)
            ))
    @ApiResponse(
            responseCode = "500",
            description = "Internal Server Error",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaymentExceptionResponse.class)
            ))
    public Mono<ResponseEntity<PaymentResponse>> refund(@Validated({RefundGroup.class}) @Valid @RequestBody PaymentRequest refundRequest,
                                                        @RequestHeader(value = "x-mgm-source", required = true) String mgmSource,
                                                        @RequestHeader(value = "x-mgm-journey-id", required = true) String mgmJourneyId,
                                                        @RequestHeader(value = "x-mgm-correlation-id", required = true) String mgmCorrelationId,
                                                        @RequestHeader(value = "x-mgm-transaction-id", required = true) String mgmTransactionId,
                                                        @RequestHeader(value = "x-mgm-client-id", required = true) String mgmClientId,
                                                        @RequestHeader(value = "x-mgm-channel", required = true) String mgmChannel,
                                                        @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = true) String jwtToken,
                                                        @RequestHeader(value = "user-agent", required = true) String userAgent,
                                                        @Parameter(hidden = true) @RequestAttribute("user") User user) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String maskedRequest = LogMaskingConverter.mask(refundRequest);
        String mgmId = refundRequest.getMgmId() != null && !refundRequest.getMgmId().isBlank() ? refundRequest.getMgmId() : " ";
        String headerParams = PaymentProcessingUtil.concatenateWithComma(mgmSource, mgmChannel, mgmJourneyId, mgmCorrelationId,
                mgmTransactionId, mgmClientId, mgmId);
        String spanTrace = PaymentProcessingUtil.concatenateWithComma(getSpanId(), getTraceId());
        String operationClass = PaymentProcessingUtil.concatenateWithComma(REFUND_OPERATION, CONTROLLER_CLASS_NAME);
        PaymentProcessingUtil.publishRequestLog(PPS_REQUEST_LOG_FORMAT, operationClass, headerParams, spanTrace, null, null, maskedRequest);
        HeadersDTO headersDTO = new HeadersDTO(mgmSource, mgmJourneyId, mgmCorrelationId, mgmTransactionId, mgmChannel, jwtToken,
                mgmClientId, userAgent);
        return paymentProcessingService.refund(refundRequest, user, headersDTO).map(paymentResponse ->
        {
            stopWatch.stop();
            String status = StatusResult.F.name();
            String result = StatusResult.F.getResult();
            String responseCode = paymentResponse.getResults().get(0).getGatewayResult().getTransaction().getResponseCode();
            if(responseCode.equals(RouterResponseCode.A.toString())){
                status = StatusResult.S.name();
                result = StatusResult.S.getResult();
            }
            String maskedResponse = LogMaskingConverter.mask(paymentResponse);
            String statusResult = PaymentProcessingUtil.concatenateWithComma(status, result);
            PaymentProcessingUtil.publishRequestLog(PPS_RESPONSE_LOG_FORMAT, operationClass, headerParams, spanTrace, statusResult, stopWatch.getTotalTimeMillis(), maskedResponse);
            return new ResponseEntity<>(paymentResponse, HttpStatus.OK);
        });
    }

    /**
     * @param paymentRequest-   Void PaymentRequest
     * @param mgmSource-        RequestHeader
     * @param mgmJourneyId-     RequestHeader
     * @param mgmCorrelationId- RequestHeader
     * @param mgmTransactionId- RequestHeader
     * @param mgmClientId-      RequestHeader
     * @param mgmChannel-       RequestHeader
     * @param jwtToken-         RequestHeader Jwt Token
     * @param userAgent-        RequestHeader
     * @param user-             RequestAttribute
     * @return VoidPaymentResponse
     */
    @PostMapping(value = "/void", consumes = "application/json", produces = "application/json")
    @ApiResponse(
            responseCode = "200",
            description = "Void the Authorized request",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaymentResponse.class)
            ))
    @ApiResponse(
            responseCode = "400",
            description = "Field Validation Failed",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaymentExceptionResponse.class)
            ))
    @ApiResponse(
            responseCode = "412",
            description = "Pre Condition Failed",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaymentExceptionResponse.class)
            ))
    @ApiResponse(
            responseCode = "500",
            description = "Internal Server Error",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaymentExceptionResponse.class)
            ))
    public Mono<ResponseEntity<PaymentResponse>> voidCall(@Validated({VoidGroup.class}) @Valid @RequestBody PaymentRequest paymentRequest,
                                                          @RequestHeader(value = "x-mgm-source", required = true) String mgmSource,
                                                          @RequestHeader(value = "x-mgm-journey-id", required = true) String mgmJourneyId,
                                                          @RequestHeader(value = "x-mgm-correlation-id", required = true) String mgmCorrelationId,
                                                          @RequestHeader(value = "x-mgm-transaction-id", required = true) String mgmTransactionId,
                                                          @RequestHeader(value = "x-mgm-client-id", required = true) String mgmClientId,
                                                          @RequestHeader(value = "x-mgm-channel", required = true) String mgmChannel,
                                                          @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = true) String jwtToken,
                                                          @RequestHeader(value = "user-agent", required = true) String userAgent,
                                                          @Parameter(hidden = true) @RequestAttribute("user") User user) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String maskedRequest = LogMaskingConverter.mask(paymentRequest);
        String mgmId = paymentRequest.getMgmId() != null && !paymentRequest.getMgmId().isBlank() ? paymentRequest.getMgmId() : " ";
        String headerParams = PaymentProcessingUtil.concatenateWithComma(mgmSource, mgmChannel, mgmJourneyId, mgmCorrelationId,
                mgmTransactionId, mgmClientId, mgmId);
        String spanTrace = PaymentProcessingUtil.concatenateWithComma(getSpanId(), getTraceId());
        String operationClass = PaymentProcessingUtil.concatenateWithComma(VOID_OPERATION, CONTROLLER_CLASS_NAME);
        PaymentProcessingUtil.publishRequestLog(PPS_REQUEST_LOG_FORMAT, operationClass, headerParams, spanTrace, null, null, maskedRequest);
        HeadersDTO headersDTO = new HeadersDTO(mgmSource, mgmJourneyId, mgmCorrelationId, mgmTransactionId, mgmChannel, jwtToken,
                mgmClientId, userAgent);
        return paymentProcessingService.voidCall(paymentRequest, user, headersDTO).map(paymentResponse ->
        {
            stopWatch.stop();
            String status = StatusResult.F.name();
            String result = StatusResult.F.getResult();
            String responseCode = paymentResponse.getResults().get(0).getGatewayResult().getTransaction().getResponseCode();
            if(responseCode.equals(RouterResponseCode.A.toString())){
                status = StatusResult.S.name();
                result = StatusResult.S.getResult();
            }
            String maskedResponse = LogMaskingConverter.mask(paymentResponse);
            String statusResult = PaymentProcessingUtil.concatenateWithComma(status, result);
            PaymentProcessingUtil.publishRequestLog(PPS_RESPONSE_LOG_FORMAT, operationClass, headerParams, spanTrace, statusResult, stopWatch.getTotalTimeMillis(), maskedResponse);
            return new ResponseEntity<>(paymentResponse, HttpStatus.OK);
        });
    }
}
