package com.mgm.payments.processing.service.model.payload.pps;

import com.mgm.payments.processing.service.model.HotelData;
import com.mgm.payments.processing.service.validation.group.*;
import com.mgm.payments.processing.service.validation.validators.AmountConstraint;
import com.mgm.payments.processing.service.validation.validators.ExpiryDateConstraint;
import com.mgm.payments.processing.service.validation.validators.SecurityCodeConstraint;
import com.mgm.payments.processing.service.model.Amount;
import com.mgm.payments.processing.service.model.Payment;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class PaymentRequest {

    @NotBlank(groups = {AuthorizeGroup.class})
    private String groupId;

    @NotBlank(groups = {AuthorizeGroup.class, CaptureGroup.class, RefundGroup.class, VoidGroup.class})
    private String clientReferenceNumber;

    private String mgmId;

    @NotBlank(groups = {CaptureGroup.class, VoidGroup.class})
    private String paymentId;

    private String orderType;

    @NotBlank(groups = {CaptureGroup.class})
    private String sessionId;

    private String clientName;

    @NotBlank(groups = {CaptureGroup.class})
    private String itemId;

    @AmountConstraint(groups = {AuthorizeGroup.class, CaptureGroup.class, RefundGroup.class, AdhocRefundGroup.class},
            message = "An amount of name 'total' is mandatory !! Total Amount Value should be between 0 and 999999999.99  !!")
    private List<Amount> amount;

    @NotNull(groups = {AuthorizeGroup.class, RefundGroup.class, AdhocRefundGroup.class})
    @Valid
    @SecurityCodeConstraint(groups = {AuthorizeGroup.class})
    @ExpiryDateConstraint(groups = {AuthorizeGroup.class, RefundGroup.class, AdhocRefundGroup.class})
    private Payment payment;
    private HotelData hotelData;
    private List<Map<String, Object>> additionalAttributes;
    private String type;

}
