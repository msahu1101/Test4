package com.mgm.payments.processing.service.repository.redis.refund;

import com.mgm.payments.processing.service.entity.redis.RefundRedisEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRedisPaymentRepository extends CrudRepository<RefundRedisEntity, String> {


}
