package com.mgm.payments.processing.service.repository.redis.payment;

import com.mgm.payments.processing.service.entity.redis.PaymentRedisEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RedisPaymentRepository  extends CrudRepository<PaymentRedisEntity, String> {
}