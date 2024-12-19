package com.mgm.payments.processing.service.validation.validators;

import com.mgm.payments.processing.service.enums.SecurityCodeIndicator;
import com.mgm.payments.processing.service.model.Payment;
import org.apache.commons.lang3.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class SecurityCodeValidator implements ConstraintValidator<SecurityCodeConstraint, Payment> {

    @Override
    public boolean isValid(Payment payment, ConstraintValidatorContext constraintValidatorContext) {
        constraintValidatorContext.disableDefaultConstraintViolation();
        if (payment!=null && payment.getTenderDetails()!=null &&
                SecurityCodeIndicator.CSC_1.equals(payment.getSecurityCodeIndicator())
                && StringUtils.isAllBlank(payment.getTenderDetails().getSecurityCode() )) {
            String buffer = "For Security code Indicator " + SecurityCodeIndicator.CSC_1 + " - " +
                    "SecurityCode is Mandatory!!";
            constraintValidatorContext.buildConstraintViolationWithTemplate(buffer).addConstraintViolation();
            return false;
        }
        return true;

    }


}
