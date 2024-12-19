package com.mgm.payments.processing.repository;

import brave.Tracer;
import com.mgm.payments.processing.service.entity.jpa.PaymentEntity;
import com.mgm.payments.processing.service.exception.PaymentProcessingException;
import com.mgm.payments.processing.service.model.HeadersDTO;
import com.mgm.payments.processing.service.model.payload.pps.exception.PaymentExceptionResponse;
import com.mgm.payments.processing.service.repository.jpa.primary.PrimaryRepository;
import com.mgm.payments.processing.service.repository.jpa.PaymentProcessingRepositoryWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;

import javax.persistence.EntityManager;

import static com.mgm.payments.processing.service.enums.ApiErrorCode.RETRY_EXCEEDS_ERROR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doNothing;

@SpringBootTest(classes = PaymentProcessingRepositoryWrapperTest.class)
@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class PaymentProcessingRepositoryWrapperTest {

    @InjectMocks
    private PaymentProcessingRepositoryWrapper paymentProcessingRepositoryWrapper;


    @Mock
    private PrimaryRepository paymentProcessingRepository;

    @Mock
    Tracer tracer;

    @Mock
    private EntityManager entityManager;


    @Test
    public void testSave() {
        PaymentEntity entity = Mockito.mock(PaymentEntity.class);
        doNothing().when(entityManager).persist(entity);
        paymentProcessingRepositoryWrapper.save(entity, new HeadersDTO());
        Mockito.verify(paymentProcessingRepository).save(entity);
    }

    @Test
    public void testFindByClientReferenceNumber() {
        paymentProcessingRepositoryWrapper.findByClientReferenceNumber("clRef", new HeadersDTO());
        Mockito.verify(paymentProcessingRepository).findByClientReferenceNumber("clRef");
    }


    @Test
    public void testFindByPaymentIdOrReferenceId() {
        HeadersDTO headersDTO = Mockito.mock(HeadersDTO.class);
        paymentProcessingRepositoryWrapper.findByPaymentIdOrReferenceId("paymentId","paymentId", headersDTO);
        Mockito.verify(paymentProcessingRepository).findByPaymentIdOrReferenceId("paymentId","paymentId");
    }

    @Test
    public void testRecover() {
        Exception e = new Exception("Mock Exception");
        PaymentExceptionResponse paymentExceptionResponse = null;
        try {
            paymentProcessingRepositoryWrapper.recover(e);
        } catch (PaymentProcessingException ex) {
            paymentExceptionResponse = ex.getExceptionResponse();
        }
        assertNotNull(paymentExceptionResponse.getDateTime());
        assertEquals(RETRY_EXCEEDS_ERROR.getCode(), paymentExceptionResponse.getErrorCode());
        assertEquals("Retry failed while executing DB Operation", paymentExceptionResponse.getErrorMessage());
        assertEquals(RETRY_EXCEEDS_ERROR.getDescription(), paymentExceptionResponse.getDeveloperMessage());
        assertEquals("Mock Exception", paymentExceptionResponse.getOriginError());

    }


}
