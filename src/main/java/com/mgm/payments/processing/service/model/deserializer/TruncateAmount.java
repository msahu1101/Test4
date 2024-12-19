package com.mgm.payments.processing.service.model.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class TruncateAmount extends  NumberDeserializers.BigDecimalDeserializer {
    @Override
    public BigDecimal deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        BigDecimal value = super.deserialize(jsonParser, deserializationContext);
        value = value!=null ? value.setScale(2, RoundingMode.DOWN):value;
        return value;
    }
}
