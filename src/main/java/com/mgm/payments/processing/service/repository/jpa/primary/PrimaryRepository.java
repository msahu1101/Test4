package com.mgm.payments.processing.service.repository.jpa.primary;

import com.mgm.payments.processing.service.entity.jpa.PaymentEntity;
import com.mgm.payments.processing.service.model.payload.PaymentEntityDTO;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrimaryRepository extends JpaRepository<PaymentEntity, String> {


    @Query("SELECT new com.mgm.payments.processing.service.model.payload.PaymentEntityDTO" +
            "(t.paymentId, t.transactionType, t.transactionStatus, t.amount, t.authorizedAmount, t.mgmToken, t.clientReferenceNumber, " +
            "t.referenceId, t.sessionId, t.mgmId, t.gatewayChainId, t.groupId, t.gatewayId,t.clientId, t.orderType, t.cardHolderName, t.tenderType, " +
            "t.cardEntryMode, t.last4DigitsOfTheCard, t.issuerType, t.currencyCode, t.billingAddress1, t.billingAddress2, " +
            "t.billingCity, t.billingCountry, t.billingState, t.billingZipcode, t.createdTimestamp, t.requestChannel)" +
            " FROM PaymentEntity t WHERE t.paymentId = :paymentId or t.referenceId = :parentId")
    List<PaymentEntityDTO> findByPaymentIdOrReferenceId(@Param("paymentId")String paymentId, @Param("referenceId")String parentId);

    @Query("SELECT new com.mgm.payments.processing.service.model.payload.PaymentEntityDTO" +
            "( t.paymentId, t.transactionType, t.transactionStatus, t.amount, t.authorizedAmount, t.mgmToken, t.clientReferenceNumber, t.referenceId, t.clientId, t.createdTimestamp, t.last4DigitsOfTheCard, t.requestChannel)" +
            " FROM PaymentEntity t WHERE t.clientReferenceNumber = :clientReferenceNumber")
    List<PaymentEntityDTO> findByClientReferenceNumber(@Param("clientReferenceNumber") String clientReferenceNumber);
}
