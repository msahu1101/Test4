package com.mgm.payments.processing.service.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
@Slf4j
public class ServiceTokenCache {
    private static ServiceTokenCache serviceTokenIns = null;

    private static Instant serviceTokenExpireTime;
    private static String serviceToken;

    private ServiceTokenCache(){

    }

    public static void setServiceTokenExpireTime(Instant serviceTokenExpireTime) {
        ServiceTokenCache.serviceTokenExpireTime = serviceTokenExpireTime;
    }
    public static synchronized void setServiceToken(String serviceToken) {
        ServiceTokenCache.serviceToken = serviceToken;
    }

    public static synchronized String getServiceToken() {
        log.debug("ServiceTokenCache: getServiceToken method Starts");
        if(serviceTokenIns==null) {
            log.debug("ServiceTokenCache: getServiceToken() serviceToken is not available in cache.");
            serviceTokenIns = new ServiceTokenCache();
            return null;
        }else {
            if (ServiceTokenCache.serviceTokenExpireTime != null && Instant.now().isBefore(ServiceTokenCache.serviceTokenExpireTime)) {
                log.debug("ServiceTokenCache: getServiceToken() serviceToken is available in cache.");
                return ServiceTokenCache.serviceToken;
            } else {
                log.debug("ServiceTokenCache: getServiceToken() serviceToken is expired.");
                return null;
            }
        }
    }

}