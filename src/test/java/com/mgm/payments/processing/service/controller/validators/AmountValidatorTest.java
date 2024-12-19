package com.mgm.payments.processing.service.controller.validators;

import com.mgm.payments.processing.service.model.Amount;
import com.mgm.payments.processing.service.validation.validators.AmountValidator;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import javax.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = AmountValidator.class)
@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
class AmountValidatorTest {

    @InjectMocks
    AmountValidator amountValidator;

    @Mock
    ConstraintValidatorContext constraintValidatorContext;

    @Test
    void isValidTest(){
        List<Amount> amountList = new ArrayList<>();
        amountList.add(Amount.builder().name("total").value(new BigDecimal(121.0)).build());
        amountList.add(Amount.builder().name("tax").value(new BigDecimal(3.0)).build());
        boolean valid = amountValidator.isValid(amountList, constraintValidatorContext);
        assertTrue(valid);
    }

    @Test
    void isValidTestMaxAmount(){
        List<Amount> amountList = new ArrayList<>();
        amountList.add(Amount.builder().name("total").value(new BigDecimal(999999999.9999)).build());
        amountList.add(Amount.builder().name("tax").value(new BigDecimal(3.0)).build());
        boolean valid = amountValidator.isValid(amountList, constraintValidatorContext);
        assertTrue(valid);
    }

    @Test
    void isValidTestNegativeAmount(){
        List<Amount> amountList = new ArrayList<>();
        amountList.add(Amount.builder().name("total").value(new BigDecimal(-9999.9999)).build());
        amountList.add(Amount.builder().name("tax").value(new BigDecimal(3.0)).build());
        boolean valid = amountValidator.isValid(amountList, constraintValidatorContext);
        assertFalse(valid);
    }


    @Test
    void isValidTestInvalidHighAmount(){
        List<Amount> amountList = new ArrayList<>();
        amountList.add(Amount.builder().name("total").value(new BigDecimal(9999999990.9999)).build());
        amountList.add(Amount.builder().name("tax").value(new BigDecimal(3.0)).build());
        boolean valid = amountValidator.isValid(amountList, constraintValidatorContext);
        assertFalse(valid);
    }
}
