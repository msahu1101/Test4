package com.mgm.payments.processing.service.external;

import brave.Tracer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mgm.payments.processing.service.MockRequestCreator;
import com.mgm.payments.processing.service.config.PPSProperties;
import com.mgm.payments.processing.service.constants.PaymentProcessingConstants;
import com.mgm.payments.processing.service.model.HeadersDTO;
import com.mgm.payments.processing.service.model.ServiceToken;
import com.mgm.payments.processing.service.model.User;
import com.mgm.payments.processing.service.model.payload.router.PaymentRouterRequest;
import com.mgm.payments.processing.service.model.payload.router.PaymentRouterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = PaymentRouterCallerTest.class)
//@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
class PaymentRouterCallerTest {

    @InjectMocks
    PaymentRouterServiceCaller prCaller;

    @Mock
    WebClient webClient;

    @Mock
    PPSProperties ppsProperties;

    @Mock
    ServiceTokenCaller tokenCaller;

    @Mock
    private Tracer tracer;

    User user;
    HeadersDTO headersDTO;

    PaymentRouterRequest prRequest;

    MockRequestCreator mockRequestCreator = new MockRequestCreator();

    @BeforeEach
    void init() {

        user = new User("00uutm8em5h0EU2eV1t7", null, null, "Mike", "Mike Doe", "mgm_app_web", "email", "mLifeNumber", "jwtToken", "service_id");
        headersDTO = new HeadersDTO("web", "1234", "12345", "123456", "WEB",
                "jwtToken", "clientId", "userAgent");
        prRequest = getPaymentRouterRequest();
        when(ppsProperties.getPaymentRouterUrl()).thenReturn("http://mockPaymentRouterUrl");
    }

    @Test
    void testInvokeRouter() throws IOException {
        String accessToken = "your_access_token";
        ServiceToken serviceToken = ServiceToken.builder().access_token(accessToken).build();
        WebClient.RequestBodyUriSpec requestBodyUriSpecMock = mock( WebClient.RequestBodyUriSpec.class);
        when(tokenCaller.getServiceAccessToken(headersDTO)).thenReturn(serviceToken);
        when(webClient.post())
                .thenReturn(requestBodyUriSpecMock);
        WebClient.RequestBodySpec requestBodySpecMock = mock( WebClient.RequestBodySpec.class);
        when(requestBodyUriSpecMock.uri("http://mockPaymentRouterUrl"
                + PaymentProcessingConstants.ROUTER_URL))
                .thenReturn(requestBodySpecMock);
        when(requestBodySpecMock.contentType(MediaType.APPLICATION_JSON))
                .thenReturn(requestBodySpecMock);
        when(requestBodySpecMock.headers(any()))
                .thenReturn(requestBodySpecMock);

        WebClient.RequestHeadersSpec requestHeadersSpecMock = mock( WebClient.RequestHeadersSpec.class);
        when(requestBodySpecMock.body(any())).thenReturn(requestHeadersSpecMock);
        WebClient.ResponseSpec responseSpecMock = mock( WebClient.ResponseSpec.class);

        when(responseSpecMock.onStatus(any(Predicate.class), any(Function.class))).thenReturn(responseSpecMock);

        when(responseSpecMock.toEntity(PaymentRouterResponse.class)).thenReturn(Mono.just(ResponseEntity.ok(mockRequestCreator.createMockRouterResponse())));
        when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);
        ResponseEntity<PaymentRouterResponse> responseEntity = prCaller.invokeRouter(prRequest, headersDTO, "paymentId").block();
        verify(webClient, times(1)).post();
        verify(requestBodyUriSpecMock, times(1)).uri("http://mockPaymentRouterUrl" + PaymentProcessingConstants.ROUTER_URL);
        verify(requestBodySpecMock, times(1)).contentType(MediaType.APPLICATION_JSON);
        verify(requestBodySpecMock,times(1)).headers(any());
        verify(requestBodySpecMock,times(1)).body(any());
        verify(responseSpecMock, times(2)).onStatus(any(Predicate.class), any(Function.class));
        verify(responseSpecMock).toEntity(PaymentRouterResponse.class);
        verify(requestHeadersSpecMock).retrieve();
        assertEquals(responseEntity.getStatusCode().value(), HttpStatus.OK.value());
    }


    private PaymentRouterRequest getPaymentRouterRequest() {
		String res  = "{\n" +
                "    \"routerFunction\": \"AUTHORIZE\",\n" +
                "    \"amount\": [\n" +
                "        {\n" +
                "            \"name\": \"cashback\",\n" +
                "            \"value\": 20\n" +
                "        },\n" +
                "        {\n" +
                "            \"name\": \"totalTax\",\n" +
                "            \"value\": 15\n" +
                "        },\n" +
                "        {\n" +
                "            \"name\": \"tip\",\n" +
                "            \"value\": 20\n" +
                "        },\n" +
                "        {\n" +
                "            \"name\": \"total\",\n" +
                "            \"value\": 160\n" +
                "        }\n" +
                "    ],\n" +
                "    \"orderReferenceNumber\": \"String\",\n" +
                "    \"mgmId\": \"12345\",\n" +
                "    \"source\": \"String\",\n" +
                "    \"gatewayRRN\": null,\n" +
                "    \"gatewayId\": \"SHFT4\",\n" +
                "    \"gatewayToken\": \"1119y1hsbhbbhn5r\",\n" +
                "    \"payment\": {\n" +
                "        \"billingAddress\": {\n" +
                "            \"firstName\": \"John\",\n" +
                "            \"middleName\": \"Peter\",\n" +
                "            \"lastName\": \"Smith\",\n" +
                "            \"address\": \"123 Main St\",\n" +
                "            \"address2\": \"Apt 8\",\n" +
                "            \"state\": \"IL\",\n" +
                "            \"city\": \"Chicago\",\n" +
                "            \"postalCode\": \"60601\",\n" +
                "            \"country\": \"US\",\n" +
                "            \"phoneNumber\": \"312-555-5555\",\n" +
                "            \"email\": \"johnsmith@email.com\"\n" +
                "        },\n" +
                "        \"tenderDetails\": {\n" +
                "            \"nameOnTender\": \"String\",\n" +
                "            \"bin\": \"\",\n" +
                "            \"tenderType\": \"GIFTCARD\",\n" +
                "            \"issueType\": \"VS\",\n" +
                "            \"mgmToken\": \"2381966411824377\",\n" +
                "            \"expireMonth\": \"03\",\n" +
                "            \"expireYear\": \"24\",\n" +
                "            \"tenderStatus\": \"Verified\",\n" +
                "            \"tenderEmail\": \"\",\n" +
                "            \"securityCode\": \"067\",\n" +
                "            \"payerId\": \"6CB2GS6AQRS5S\",\n" +
                "            \"paymentMethodNonce\": \"fd66f90e-c8ec-0363-6b45-aeb930175e43\",\n" +
                "            \"deviceData\": \"{\\\"correlation_id\\\":\\\"010e4 a744d78644f971fc5f9dc1c43aa\\\"}\"\n" +
                "        },\n" +
                "        \"securityCodeIndicator\": \"CSC_1\",\n" +
                "        \"cardPresent\": \"Y\",\n" +
                "        \"currencyCode\": \"USD\"\n" +
                "    },\n" +
                "    \"additionalAttributes\": [\n" +
                "        {\n" +
                "            \"name\": \"customerReference\",\n" +
                "            \"value\": \"String\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"name\": \"productDescriptors\",\n" +
                "            \"value\": \"Hamburger\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"name\": \"productDescriptors\",\n" +
                "            \"value\": \"Fries\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"name\": \"productDescriptors\",\n" +
                "            \"value\": \"Pizza\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"name\": \"productDescriptors\",\n" +
                "            \"value\": \"Pasta\"\n" +
                "        }\n" +
                "    ]\n" +
                "}";
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		try {
			return objectMapper.readValue(res, PaymentRouterRequest.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
}
