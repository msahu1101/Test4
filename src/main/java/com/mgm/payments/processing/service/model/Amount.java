package com.mgm.payments.processing.service.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mgm.payments.processing.service.model.deserializer.TruncateAmount;
import lombok.*;

import java.math.BigDecimal;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class Amount {
    private String name;

    @JsonDeserialize(using = TruncateAmount.class)
    private BigDecimal value;
}
