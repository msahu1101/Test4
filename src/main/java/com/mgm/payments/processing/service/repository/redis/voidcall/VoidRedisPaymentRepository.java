package com.mgm.payments.processing.service.repository.redis.voidcall;

import com.mgm.payments.processing.service.entity.redis.VoidRedisEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoidRedisPaymentRepository extends CrudRepository<VoidRedisEntity, String> {


}
