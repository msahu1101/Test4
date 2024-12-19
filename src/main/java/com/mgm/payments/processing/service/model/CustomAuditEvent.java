package com.mgm.payments.processing.service.model;

import lombok.Data;
import org.springframework.context.ApplicationEvent;

@Data
public class CustomAuditEvent extends ApplicationEvent {

    private transient AuditRequest auditRequest;

    public CustomAuditEvent(Object source, AuditRequest auditRequest) {
        super(source);
        this.auditRequest = auditRequest;
    }


}
