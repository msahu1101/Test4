package com.mgm.payments.processing.service.service;

import com.mgm.payments.processing.service.model.*;
import com.mgm.payments.processing.service.model.payload.pps.PaymentRequest;
import com.mgm.payments.processing.service.model.payload.pps.PaymentResponse;
import com.mgm.payments.processing.service.repository.jpa.PaymentProcessingRepositoryWrapper;
import com.mgm.payments.processing.service.service.processor.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PaymentProcessingService {

    PaymentProcessingRepositoryWrapper repository;
    private final PaymentProcessor authorizePaymentProcessor;
    private final PaymentProcessor capturePaymentProcessor;
    private final PaymentProcessor voidPaymentProcessor;
    private final PaymentProcessor refundPaymentProcessor;


    public PaymentProcessingService(PaymentProcessingRepositoryWrapper repository,
                                    AuthorizePaymentProcessor authorizePaymentProcessor,
                                    CapturePaymentProcessor capturePaymentProcessor,
                                    VoidPaymentProcessor voidPaymentProcessor,
                                    RefundPaymentProcessor refundPaymentProcessor
                                    ) {
        this.repository = repository;
        this.authorizePaymentProcessor = authorizePaymentProcessor;
        this.capturePaymentProcessor = capturePaymentProcessor;
        this.voidPaymentProcessor = voidPaymentProcessor;
        this.refundPaymentProcessor = refundPaymentProcessor;

    }


    /**
     * Service Layer implements the business logic for authorize
     *
     * @param paymentRequest- Authorize Call RequestBody DTO
     * @param user-           Users Data
     * @param headersDTO-     headers Params
     * @return PaymentResponse
     */
    public Mono<PaymentResponse> authorize(PaymentRequest paymentRequest, User user, HeadersDTO headersDTO)
    {
        return authorizePaymentProcessor.process(paymentRequest, user,headersDTO);
    }


    /**
     * Service Layer implements the business logic for Capture
     *
     * @param paymentRequest- Capture RequestBody DTO
     * @param user-           User Details
     * @param headersDTO-     header Params
     * @return PaymentResponse
     */
    public Mono<PaymentResponse> capture(PaymentRequest paymentRequest, User user, HeadersDTO headersDTO){
        return capturePaymentProcessor.process(paymentRequest, user, headersDTO);
    }


    public Mono<PaymentResponse> refund(PaymentRequest paymentRequest, User user, HeadersDTO headersDTO) {
        return refundPaymentProcessor.process(paymentRequest,user, headersDTO );
    }

    /**
     * Service Layer implements the Business Logics for the void transaction
     *
     * @param paymentRequest- Void RequestBody
     * @param user-           UserDetails
     * @param headersDTO-     HeadersDetails
     * @return PaymentResponse
     */
    public Mono<PaymentResponse> voidCall(PaymentRequest paymentRequest, User user, HeadersDTO headersDTO){
        return voidPaymentProcessor.process(paymentRequest,user, headersDTO );
    }



}
