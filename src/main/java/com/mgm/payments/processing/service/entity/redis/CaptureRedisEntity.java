package com.mgm.payments.processing.service.entity.redis;

import com.mgm.payments.processing.service.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.RedisHash;

import javax.persistence.Column;
import javax.persistence.Id;
import java.math.BigDecimal;

@RedisHash(value = "capture", timeToLive = 1800)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaptureRedisEntity {


    @Id
    @Column(updatable = false,
            nullable = false)
    private String id; // auth paymentId

    private TransactionStatus transactionStatus;
    private BigDecimal amount;
}
