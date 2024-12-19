package com.mgm.payments.processing.service.model;

import lombok.*;
import org.springframework.stereotype.Component;

@Component
@Data
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User {

    private String mgmId;

    private String uid;

    private String cid;

    private String firstName;

    private String lastName;

    private String mgmRole;

    private String email;

    private String mlifeNumber;

    private String jwtToken;

    private String serviceId;

}