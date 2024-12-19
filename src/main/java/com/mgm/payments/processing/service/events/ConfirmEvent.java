package com.mgm.payments.processing.service.events;


import com.mgm.payments.processing.service.model.CaptureConfirm;
import com.mgm.payments.processing.service.model.HeadersDTO;
import lombok.Builder;
import lombok.Data;
import java.io.Serializable;
import org.springframework.context.ApplicationEvent;

@Data
@Builder
public class ConfirmEvent extends ApplicationEvent implements Serializable {

    private transient CaptureConfirm captureConfirm;
    private transient HeadersDTO headersDTO;
    public ConfirmEvent(CaptureConfirm captureConfirm, HeadersDTO headersDTO) {
        super(captureConfirm);
        this.captureConfirm = captureConfirm;
        this.headersDTO = headersDTO;
    }


}
