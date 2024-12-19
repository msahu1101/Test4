package com.mgm.payments.processing.service.model;

import com.mgm.payments.processing.service.constants.PaymentProcessingConstants;
import com.mgm.payments.processing.service.validation.group.AuthorizeGroup;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class BillingAddress {

    @Size(max = PaymentProcessingConstants.MAX_ADDRESS_LENGTH, message = "The Address line cannot be more than than 70 characters")
    private String address;

    @Size(max = PaymentProcessingConstants.MAX_ADDRESS_LENGTH, message = "The Address 2 line cannot be more than than 70 characters")
    private String address2;

    private String state;

    private String city;

  
    private String postalCode;

    private String country;
}
