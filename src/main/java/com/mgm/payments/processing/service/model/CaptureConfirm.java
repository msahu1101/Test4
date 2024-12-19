package com.mgm.payments.processing.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CaptureConfirm {
    private String sessionId;
    private String orderStatus;
    private String processorResponseText;
    private String mgmId;
    private String orderReferenceNumber;
    private List<Amount> amount;
    private String responseCode;
    private String avsResult;
}
