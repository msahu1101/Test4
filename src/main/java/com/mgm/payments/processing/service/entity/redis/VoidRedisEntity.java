package com.mgm.payments.processing.service.entity.redis;

import com.mgm.payments.processing.service.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.RedisHash;

import javax.persistence.Column;
import javax.persistence.Id;

@RedisHash(value = "void", timeToLive = 1800)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoidRedisEntity {


    @Id
    @Column(updatable = false,
            nullable = false)
    private String id; // paymentId
    private TransactionStatus transactionStatus;
}
