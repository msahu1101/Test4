package com.mgm.payments.processing.service.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import brave.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mgm.payments.processing.service.model.HeadersDTO;
import com.mgm.payments.processing.service.model.User;
import com.mgm.payments.processing.service.model.payload.pps.PaymentRequest;
import com.mgm.payments.processing.service.model.payload.pps.PaymentResponse;
import com.mgm.payments.processing.service.service.PaymentProcessingService;
import reactor.core.publisher.Mono;


@SpringBootTest(classes = PaymentProcessingControllerTest.class)
@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
class PaymentProcessingControllerTest {

	@InjectMocks
	private PaymentProcessingController PaymentProcessingController;

	@Mock
	private  PaymentProcessingService paymentProcessingService;
	@Mock
	private Tracer tracer;

	private User user;
	private HeadersDTO headersDTO;

	@BeforeEach
	void init() {
		user = new User("00uutm8em5h0EU2eV1t7", null, null, "Mike", "Mike Doe", "mgm_app_web", "email", "mLifeNumber", "jwtToken", "service_id");
		headersDTO = new HeadersDTO("web", "1234", "12345", "123456", "WEB", "jwtToken", "clientId", "userAgent");
	}

	@Test
	void testGetHealth(){
		String health = PaymentProcessingController.getHealth();
		assertEquals("ok", health);
	}

	@Test
	void testAuthorizeEndpoint() {
		doReturn(Mono.just(getAuthResponse())).when(paymentProcessingService).authorize(any(), any(), any());
		ResponseEntity<PaymentResponse> response = PaymentProcessingController.authorize(getAuthRequest(), "mgmSource", "mgmJourneyId", "mgmCorrelationId", "mgmTransactionId", "mgmClientId", "mgmChannel",
				"jwtToken", "userAgent", user).block();
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	@Test
	void testCaptureEndpoint() {
		doReturn(Mono.just(getCaptureResponse())).when(paymentProcessingService).capture(any(), any(), any());
		ResponseEntity<PaymentResponse> response = PaymentProcessingController.capture(getAuthRequest(), "mgmSource",
				"mgmJourneyId", "mgmCorrelationId", "mgmTransactionId", "mgmClientId", "mgmChannel",
				"jwtToken", "userAgent", user).block();
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	@Test
	void testRefundEndpoint() {
		doReturn(Mono.just(getRefundResponse())).when(paymentProcessingService).refund(any(), any(), any());
		when(paymentProcessingService.refund(getRefundRequest(), user, headersDTO)).thenReturn(Mono.just(getRefundResponse()));
		ResponseEntity<PaymentResponse> response = PaymentProcessingController.refund(getAuthRequest(), "mgmSource",
				 "mgmJourneyId", "mgmCorrelationId", "mgmTransactionId", "mgmClientId", "mgmChannel",
				"jwtToken", "userAgent", user).block();
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}


	@Test
	void testVoidEndpoint() {
		doReturn(Mono.just(getCaptureResponse())).when(paymentProcessingService).voidCall(any(), any(), any());
		ResponseEntity<PaymentResponse> response = PaymentProcessingController.voidCall(getAuthRequest(),  "mgmSource",
				"mgmJourneyId", "mgmCorrelationId", "mgmTransactionId", "mgmClientId", "mgmChannel",
				"jwtToken", "userAgent", user).block();
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	PaymentRequest getAuthRequest() {

		String auth = "{\r\n" + "    \"orderReferenceNumber\": \"123456\",\r\n"
				+ "    \"orderConfirmationNumber\": \"23124\",\r\n" + "    \"mgmId\": \"00u1tdaotm5Y20maD0h8\",\r\n"
				+ "    \"orderType\": \"ROOM\",\r\n" + "    \"sessionId\" : \"session123\",\r\n"
				+ "    \"clientName\": \"john\",\r\n" + "    \"amount\": [\r\n" + "       {\r\n"
				+ "            \"name\": \"total\",\r\n" + "            \"value\": 121\r\n" + "        }\r\n"
				+ "    ],\r\n" + "    \"payment\": {\r\n" + "       \"billingAddress\": {\r\n"
				+ "                \"firstName\": \"John\", \r\n" + "                \"middleName\": \"Peter\",\r\n"
				+ "                \"lastName\": \"Smith\", \r\n" + "                \"address\": \"123 Main St\", \r\n"
				+ "                \"address2\": \"Apt 8\", \r\n" + "                \"state\": \"IL\", \r\n"
				+ "                \"city\": \"Chicago\", \r\n" + "                \"postalCode\": \"60601\", \r\n"
				+ "                \"country\": \"US\", \r\n" + "                \"phoneNumber\": 3125555555, \r\n"
				+ "                \"email\": \"johnsmith@email.com\" \r\n" + "            },\r\n"
				+ "            \"currencyCode\": \"USD\",\r\n" + "            \"securityCodeIndicator\": \"CSC_2\",\r\n"
				+ "            \"cardPresent\": \"Y\",\r\n" + "            \"tenderDetails\": {\r\n"
				+ "                \"nameOnTender\": \"John\", \r\n"
				+ "                \"maskedCardNumber\": \"\", \r\n"
				+ "                \"tenderType\": \"CREDITCARD\", \r\n"
				+ "                \"issuerType\": \"BC\", \r\n"
				+ "                \"mgmToken\": \"12341234123412\", \r\n"
				+ "                \"expireMonth\": \"05\",\r\n" + "                \"expireYear\": \"18\", \r\n"
				+ "                \"tenderStatus\": \"Verified\",\r\n" + "                \"tenderEmail\": \"\" ,\r\n"
				+ "                \"payerId\": \"6CB2GS6AQRS5S\", \r\n"
				+ "                \"paymentMethodNonce\": \"fd66f90e-c8ec-0363-6b45-aeb930175e43\",\r\n"
				+ "                \"deviceData\": \"String\",\r\n" + "                \"securityCode\" : \"\"\r\n"
				+ "            }\r\n" + "    \r\n" + "    },\r\n" + "   \"additionalAttributes\": [\r\n"
				+ "        {\r\n" + "            \"name\": \"customerReference\",\r\n"
				+ "            \"value\": \"Can be any type, define as Object\"\r\n" + "        },\r\n"
				+ "        {\r\n" + "            \"name\": \"productDescriptors\",\r\n"
				+ "            \"value\": \"\"\r\n" + "        }\r\n" + "    ]\r\n" + "}";
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		try {
			return objectMapper.readValue(auth, PaymentRequest.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}

	}

	PaymentResponse getAuthResponse() {

		String authResponse = "{\r\n" + "    \"results\": [\r\n" + "        {\r\n"
				+ "            \"gatewayResult\": {\r\n" + "                \"orderReferenceNumber\": \"123456\",\r\n"
				+ "                \"mgmId\": \"00u1tdaotm5Y20maD0h8\",\r\n" + "                \"card\": {\r\n"
				+ "                    \"maskedCardNumber\": \"XXXXXXXXXXXX4111\",\r\n"
				+ "                    \"gatewayId\": \"SHFT4\"\r\n" + "                },\r\n"
				+ "                \"transaction\": {\r\n"
				+ "                    \"authorizationCode\": \"198399\",\r\n"
				+ "                    \"authSource\": \"E\",\r\n" + "                    \"gatewayResponse\": {\r\n"
				+ "                        \"reasonCode\": \"04\",\r\n"
				+ "                        \"reasonDescription\": \"PickUpCard_NoFraud\",\r\n"
				+ "                        \"reattemptPermission\": \"Reattempt\"\r\n" + "                    },\r\n"
				+ "                    \"paymentId\": \"af916f31-2c9f-4af7-b602-a65b585f7bd4\",\r\n"
				+ "                    \"responseCode\": \"A\",\r\n"
				+ "                    \"retrievalReference\": \"402F9H0230S0\",\r\n"
				+ "                    \"saleFlag\": \"S\",\r\n" + "                    \"amex\": {\r\n"
				+ "                        \"propertyCode\": \"21546782948\"\r\n" + "                    },\r\n"
				+ "                    \"deferredAuth\": \"D\"\r\n" + "                },\r\n"
				+ "                \"dateTime\": \"2023-11-23T13:31:12.8344037+05:30\",\r\n"
				+ "                \"amount\": [\r\n" + "                    {\r\n"
				+ "                        \"name\": \"authorized_amount\",\r\n"
				+ "                        \"value\": 120.0\r\n" + "                    },\r\n"
				+ "                    {\r\n" + "                        \"name\": \"total\",\r\n"
				+ "                        \"value\": 121.0\r\n" + "                    }\r\n"
				+ "                ],\r\n" + "                \"server\": \"TM01CE\"\r\n" + "            }\r\n"
				+ "        }\r\n" + "    ]\r\n" + "}";

		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		try {
			return objectMapper.readValue(authResponse, PaymentResponse.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}

	}

	PaymentRequest getCaptureRequest() {

		String request = "{\r\n" + "    \"orderReferenceNumber\": \"101\",\r\n"
				+ "    \"paymentId\": \"7ac166d5-389b-4ccb-bd6e-69063f5643d1\",\r\n"
				+ "    \"orderConfirmationNumber\": \"1012\",\r\n" + "    \"mgmId\": \"00u1tdaotm5Y20maD0h8\",\r\n"
				+ "    \"orderType\": \"ROOM\",\r\n" + "    \"sessionId\" : \"session123\",\r\n"
				+ "    \"clientName\": \"john\",\r\n" + "    \"amount\": [\r\n" + "       {\r\n"
				+ "            \"name\": \"total\",\r\n" + "            \"value\": 121\r\n" + "        }\r\n"
				+ "    ],\r\n" + "    \"payment\": {\r\n" + "       \"billingAddress\": {\r\n"
				+ "                \"firstName\": \"John\", \r\n" + "                \"middleName\": \"Peter\",\r\n"
				+ "                \"lastName\": \"Smith\", \r\n" + "                \"address\": \"123 Main St\", \r\n"
				+ "                \"address2\": \"Apt 8\", \r\n" + "                \"state\": \"IL\", \r\n"
				+ "                \"city\": \"Chicago\", \r\n" + "                \"postalCode\": \"60601\", \r\n"
				+ "                \"country\": \"US\", \r\n" + "                \"phoneNumber\": 3125555555, \r\n"
				+ "                \"email\": \"johnsmith@email.com\" \r\n" + "            },\r\n"
				+ "            \"currencyCode\": \"USD\",\r\n" + "            \"securityCodeIndicator\": \"CSC_2\",\r\n"
				+ "            \"cardPresent\": \"Y\",\r\n" + "            \"tenderDetails\": {\r\n"
				+ "                \"nameOnTender\": \"John\", \r\n"
				+ "                \"maskedCardNumber\": \"\", \r\n"
				+ "                \"tenderType\": \"CREDITCARD\", \r\n"
				+ "                \"issuerType\": \"BC\", \r\n"
				+ "                \"mgmToken\": \"12341234123412\", \r\n"
				+ "                \"expireMonth\": \"05\",\r\n" + "                \"expireYear\": \"18\", \r\n"
				+ "                \"tenderStatus\": \"Verified\",\r\n" + "                \"tenderEmail\": \"\" ,\r\n"
				+ "                \"payerId\": \"6CB2GS6AQRS5S\", \r\n"
				+ "                \"paymentMethodNonce\": \"fd66f90e-c8ec-0363-6b45-aeb930175e43\",\r\n"
				+ "                \"deviceData\": \"String\",\r\n" + "                \"securityCode\" : \"\"\r\n"
				+ "            }\r\n" + "    \r\n" + "    },\r\n" + "   \"additionalAttributes\": [\r\n"
				+ "        {\r\n" + "            \"name\": \"customerReference\",\r\n"
				+ "            \"value\": \"Can be any type, define as Object\"\r\n" + "        },\r\n"
				+ "        {\r\n" + "            \"name\": \"productDescriptors\",\r\n"
				+ "            \"value\": \"\"\r\n" + "        }\r\n" + "    ]\r\n" + "}";
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		try {
			return objectMapper.readValue(request, PaymentRequest.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}

	}

	PaymentResponse getCaptureResponse() {

		String response = "{\r\n" + "    \"results\": [\r\n" + "        {\r\n" + "            \"gatewayResult\": {\r\n"
				+ "                \"orderReferenceNumber\": \"101\",\r\n"
				+ "                \"mgmId\": \"00u1tdaotm5Y20maD0h8\",\r\n" + "                \"card\": {\r\n"
				+ "                    \"maskedCardNumber\": \"XXXXXXXXXXXX4111\",\r\n"
				+ "                    \"gatewayId\": \"SHFT4\"\r\n" + "                },\r\n"
				+ "                \"transaction\": {\r\n"
				+ "                    \"authorizationCode\": \"198399\",\r\n"
				+ "                    \"authSource\": \"E\",\r\n" + "                    \"gatewayResponse\": {\r\n"
				+ "                        \"reasonCode\": \"04\",\r\n"
				+ "                        \"reasonDescription\": \"PickUpCard_NoFraud\",\r\n"
				+ "                        \"reattemptPermission\": \"Reattempt\"\r\n" + "                    },\r\n"
				+ "                    \"paymentId\": \"4ada75ea-a5a4-4263-a255-e02f7c8327a9\",\r\n"
				+ "                    \"responseCode\": \"A\",\r\n"
				+ "                    \"retrievalReference\": \"402F9H0230S0\",\r\n"
				+ "                    \"saleFlag\": \"S\",\r\n" + "                    \"amex\": {\r\n"
				+ "                        \"propertyCode\": \"21546782948\"\r\n" + "                    },\r\n"
				+ "                    \"deferredAuth\": \"D\"\r\n" + "                },\r\n"
				+ "                \"dateTime\": \"2023-11-24T07:43:54.2842807Z\",\r\n"
				+ "                \"amount\": [\r\n" + "                    {\r\n"
				+ "                        \"name\": \"authorized_amount\",\r\n"
				+ "                        \"value\": 120.0\r\n" + "                    },\r\n"
				+ "                    {\r\n" + "                        \"name\": \"total\",\r\n"
				+ "                        \"value\": 121.0\r\n" + "                    }\r\n"
				+ "                ],\r\n" + "                \"server\": \"TM01CE\"\r\n" + "            }\r\n"
				+ "        }\r\n" + "    ]\r\n" + "}";

		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		try {
			return objectMapper.readValue(response, PaymentResponse.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}

	}

	PaymentRequest getRefundRequest() {

		String request = "{\r\n" + "    \"orderReferenceNumber\": \"123456\",\r\n"
				+ "    \"paymentId\": \"e5da149d-31e0-4bf2-8637-f536043e60b8\",\r\n"
				+ "    \"orderConfirmationNumber\": \"23124\",\r\n" + "    \"mgmId\": \"00u1tdaotm5Y20maD0h8\",\r\n"
				+ "    \"orderType\": \"ROOM\",\r\n" + "    \"sessionId\" : \"session123\",\r\n"
				+ "    \"clientName\": \"john\",\r\n" + "    \"amount\": [\r\n" + "       {\r\n"
				+ "            \"name\": \"total\",\r\n" + "            \"value\": 20\r\n" + "        }\r\n"
				+ "    ],\r\n" + "    \"payment\": {\r\n" + "       \"billingAddress\": {\r\n"
				+ "                \"firstName\": \"John\", \r\n" + "                \"middleName\": \"Peter\",\r\n"
				+ "                \"lastName\": \"Smith\", \r\n" + "                \"address\": \"123 Main St\", \r\n"
				+ "                \"address2\": \"Apt 8\", \r\n" + "                \"state\": \"IL\", \r\n"
				+ "                \"city\": \"Chicago\", \r\n" + "                \"postalCode\": \"60601\", \r\n"
				+ "                \"country\": \"US\", \r\n" + "                \"phoneNumber\": 3125555555, \r\n"
				+ "                \"email\": \"johnsmith@email.com\" \r\n" + "            },\r\n"
				+ "            \"currencyCode\": \"USD\",\r\n" + "            \"securityCodeIndicator\": \"CSC_2\",\r\n"
				+ "            \"cardPresent\": \"Y\",\r\n" + "            \"tenderDetails\": {\r\n"
				+ "                \"nameOnTender\": \"John\", \r\n"
				+ "                \"maskedCardNumber\": \"\", \r\n"
				+ "                \"tenderType\": \"CREDITCARD\", \r\n"
				+ "                \"issuerType\": \"BC\", \r\n"
				+ "                \"mgmToken\": \"12341234123412\", \r\n"
				+ "                \"expireMonth\": \"05\",\r\n" + "                \"expireYear\": \"18\", \r\n"
				+ "                \"tenderStatus\": \"Verified\",\r\n" + "                \"tenderEmail\": \"\" ,\r\n"
				+ "                \"payerId\": \"6CB2GS6AQRS5S\", \r\n"
				+ "                \"paymentMethodNonce\": \"fd66f90e-c8ec-0363-6b45-aeb930175e43\",\r\n"
				+ "                \"deviceData\": \"String\",\r\n" + "                \"securityCode\" : \"\"\r\n"
				+ "            }\r\n" + "    \r\n" + "    },\r\n" + "   \"additionalAttributes\": [\r\n"
				+ "        {\r\n" + "            \"name\": \"customerReference\",\r\n"
				+ "            \"value\": \"Can be any type, define as Object\"\r\n" + "        },\r\n"
				+ "        {\r\n" + "            \"name\": \"productDescriptors\",\r\n"
				+ "            \"value\": \"\"\r\n" + "        }\r\n" + "    ]\r\n" + "}";
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		try {
			return objectMapper.readValue(request, PaymentRequest.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}

	}

	PaymentResponse getRefundResponse() {

		String response = "{\r\n" + "    \"results\": [\r\n" + "        {\r\n" + "            \"gatewayResult\": {\r\n"
				+ "                \"orderReferenceNumber\": \"123456\",\r\n"
				+ "                \"mgmId\": \"00u1tdaotm5Y20maD0h8\",\r\n" + "                \"card\": {\r\n"
				+ "                    \"maskedCardNumber\": \"XXXXXXXXXXXX4111\",\r\n"
				+ "                    \"gatewayId\": \"SHFT4\"\r\n" + "                },\r\n"
				+ "                \"transaction\": {\r\n"
				+ "                    \"authorizationCode\": \"198399\",\r\n"
				+ "                    \"authSource\": \"E\",\r\n" + "                    \"gatewayResponse\": {\r\n"
				+ "                        \"reasonCode\": \"04\",\r\n"
				+ "                        \"reasonDescription\": \"PickUpCard_NoFraud\",\r\n"
				+ "                        \"reattemptPermission\": \"Reattempt\"\r\n" + "                    },\r\n"
				+ "                    \"paymentId\": \"8a3ff2d6-f425-4590-9b68-50262ac32afc\",\r\n"
				+ "                    \"responseCode\": \"A\",\r\n"
				+ "                    \"retrievalReference\": \"402F9H0230S0\",\r\n"
				+ "                    \"saleFlag\": \"S\",\r\n" + "                    \"amex\": {\r\n"
				+ "                        \"propertyCode\": \"21546782948\"\r\n" + "                    },\r\n"
				+ "                    \"deferredAuth\": \"D\"\r\n" + "                },\r\n"
				+ "                \"dateTime\": \"2023-11-23T12:45:27.9279549Z\",\r\n"
				+ "                \"amount\": [\r\n" + "                    {\r\n"
				+ "                        \"name\": \"authorized_amount\",\r\n"
				+ "                        \"value\": 120.0\r\n" + "                    },\r\n"
				+ "                    {\r\n" + "                        \"name\": \"total\",\r\n"
				+ "                        \"value\": 20.0\r\n" + "                    }\r\n" + "                ],\r\n"
				+ "                \"server\": \"TM01CE\"\r\n" + "            }\r\n" + "        }\r\n" + "    ]\r\n"
				+ "}";

		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		try {
			return objectMapper.readValue(response, PaymentResponse.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}

	}

}
