package com.mgm.payments.processing.service.repository.redis.auth;

import com.mgm.payments.processing.service.entity.redis.AuthorizeRedisEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthorizeRedisPaymentRepository extends CrudRepository<AuthorizeRedisEntity, String> {


    Optional<AuthorizeRedisEntity> findByClientReferenceNumber(String clientReferenceNumber);
}
