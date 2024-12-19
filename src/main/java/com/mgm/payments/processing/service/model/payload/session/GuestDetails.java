package com.mgm.payments.processing.service.model.payload.session;

import com.mgm.payments.processing.service.model.BillingAddress;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class GuestDetails {
    private String mgmId;
    private String firstName;
    private String middleName;
    private String lastName;
    private String phoneNumber;
    private boolean loggedIn;
    private String email;
    private String created;
    private String lastModifiedDate;
    private BillingAddress address;
}
