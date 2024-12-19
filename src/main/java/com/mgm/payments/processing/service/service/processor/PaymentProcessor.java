package com.mgm.payments.processing.service.service.processor;

import com.azure.core.annotation.Head;
import com.mgm.payments.processing.service.entity.jpa.PaymentEntity;
import com.mgm.payments.processing.service.model.HeadersDTO;
import com.mgm.payments.processing.service.model.User;
import com.mgm.payments.processing.service.model.payload.pps.PaymentRequest;
import com.mgm.payments.processing.service.model.payload.pps.PaymentResponse;
import com.mgm.payments.processing.service.model.payload.router.PaymentRouterResponse;
import com.mgm.payments.processing.service.model.payload.session.PaymentSession;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PaymentProcessor {

    public Mono<PaymentResponse> process(PaymentRequest paymentRequest, User user, HeadersDTO headersDTO);


    public void validateInputRequest(PaymentRequest paymentRequest, List<PaymentEntity> paymentList, HeadersDTO headersDTO);

    public PaymentSession retrieveClientDetailsFromSession(PaymentRequest paymentRequest, HeadersDTO headersDTO);

    public void populatePaymentEntity(HeadersDTO headersDTO, PaymentRequest request, List<PaymentEntity> paymentList, PaymentEntity paymentEntity, User user);

    public void createRedisCacheEntry(PaymentEntity paymentEntity, HeadersDTO headersDTO);

    public Mono<PaymentRouterResponse> invokePaymentRouter(PaymentEntity paymentEntity, PaymentRequest paymentRequest, HeadersDTO headersDTO);

    public void updateRouterResponseInTheDBRecord(PaymentEntity payment, PaymentRouterResponse prResponse, User user, HeadersDTO headersDTO, List<PaymentEntity> paymentList);


    void mapPaymentRouterResponseToPaymentResponse(PaymentEntity paymentEntity, PaymentRequest paymentRequest, PaymentRouterResponse prResponse,
                                                              PaymentResponse paymentResponse, HeadersDTO headersDTO);

    public void updateDBOnFailure(PaymentEntity payment, HeadersDTO headersDTO, Exception e);
  
}
