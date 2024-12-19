package com.mgm.payments.processing.service.security;

import com.mgm.payments.processing.service.constants.PaymentProcessingConstants;
import com.mgm.payments.processing.service.model.User;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.*;

@Component
public class RequestFilter implements Filter {
    
    Logger log = LoggerFactory.getLogger(RequestFilter.class);

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        String authzHdr = httpServletRequest.getHeader("authorization");
        User user = StringUtils.isNotBlank(authzHdr) ? getSourceFromJWT(authzHdr) : null;
        servletRequest.setAttribute("user", user);

        MDC.put("correlationId", httpServletRequest.getHeader(CORRELATION_ID));
        MDC.put("journeyId", httpServletRequest.getHeader(JOURNEY_ID));
        MDC.put("transactionId", httpServletRequest.getHeader(TRANSACTION_ID));

        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        httpServletResponse.setHeader(PaymentProcessingConstants.MGM_CLUSTER_NAME, System.getenv(PaymentProcessingConstants.ENV_CLUSTER_NAME));

        filterChain.doFilter(servletRequest, servletResponse);
    }

    public User getSourceFromJWT(String jwt) {

        if (StringUtils.isBlank(jwt)) {
            return new User();
        }

        String cidAttr = StringUtils.EMPTY;
        User user = null;
        try {
            JWT djwt = JWTParser.parse(jwt.replace(BEARER, StringUtils.EMPTY));
            JWTClaimsSet cs = djwt.getJWTClaimsSet();
            cidAttr = (String) cs.getClaim("cid");
            log.debug("Source Identified from JWT: {}", cidAttr);
            user = setUserProps(jwt);


        } catch (ParseException e) {
            log.error("JWT Parser Exception:: Error while decoding JWT: {}", e.toString());
        }
        return user;
    }

    public User setUserProps(String jwt) {
        JWT djwt = null;
        User user = new User();
        try {
            djwt = JWTParser.parse(jwt.replace(BEARER, StringUtils.EMPTY));
            JWTClaimsSet cs = djwt.getJWTClaimsSet();

            user.setMgmId((String) cs.getClaim("com.mgm.id"));
            user.setFirstName((String) cs.getClaim("given_name"));
            user.setLastName((String) cs.getClaim("name"));
            user.setMgmRole((String) cs.getClaim("cid"));
            user.setJwtToken((BEARER + jwt));
            String serviceId = (String) cs.getClaim("sub");
            user.setServiceId(serviceId);
            log.info("Payment Processing Service: Token generated with Service Id : {}", serviceId);
        } catch (ParseException e) {
            log.error("JWT Parser Exception - Error while decoding JWT: {}", e.toString());
        }
        return user;
    }
}
