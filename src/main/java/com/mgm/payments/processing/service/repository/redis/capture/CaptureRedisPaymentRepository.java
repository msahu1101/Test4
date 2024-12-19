package com.mgm.payments.processing.service.repository.redis.capture;

import com.mgm.payments.processing.service.entity.redis.CaptureRedisEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CaptureRedisPaymentRepository extends CrudRepository<CaptureRedisEntity, String> {


}
