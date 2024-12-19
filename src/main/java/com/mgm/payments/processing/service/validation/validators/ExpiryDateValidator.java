package com.mgm.payments.processing.service.validation.validators;

import com.mgm.payments.processing.service.model.Payment;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.time.ZonedDateTime;

public class ExpiryDateValidator implements ConstraintValidator<ExpiryDateConstraint, Payment> {

    @Override
    public boolean isValid(Payment payment, ConstraintValidatorContext constraintValidatorContext) {
        constraintValidatorContext.disableDefaultConstraintViolation();
        if (payment != null && payment.getTenderDetails() != null && !StringUtils.isBlank(payment.getTenderDetails().getExpireMonth()) &&
                !StringUtils.isBlank(payment.getTenderDetails().getExpireYear())) {
            int expireMonth = 0;
            int expireYear = 0;
            try{
                expireMonth = Integer.parseInt(payment.getTenderDetails().getExpireMonth());
                expireYear = Integer.parseInt(payment.getTenderDetails().getExpireYear());
            } catch (NumberFormatException e) {
                HibernateConstraintValidatorContext context = constraintValidatorContext.unwrap(HibernateConstraintValidatorContext.class);
                context.buildConstraintViolationWithTemplate("Expiry Month and Year should be in numeric format !!").addConstraintViolation();
                return false;
            }

            if(expireMonth < 1 || expireMonth > 12) {
                HibernateConstraintValidatorContext context = constraintValidatorContext.unwrap(HibernateConstraintValidatorContext.class);
                context.addMessageParameter("expireMonth", expireMonth);
                context.buildConstraintViolationWithTemplate("Expiry Month: {expireMonth} is invalid !! Month should be in format MM and should be between 1 and 12 !!").addConstraintViolation();
                return false;
            }
            if(expireYear < 0 || expireYear > 99) {
                HibernateConstraintValidatorContext context = constraintValidatorContext.unwrap(HibernateConstraintValidatorContext.class);
                context.addMessageParameter("expireYear", expireYear);
                context.buildConstraintViolationWithTemplate("Expiry Year: {expireYear} is invalid !! Year should be in format YY !!").addConstraintViolation();
                return false;
            }
            int currentMonth = ZonedDateTime.now().getMonthValue();
            int currentYear = ZonedDateTime.now().getYear() % 100;
            if (expireYear < currentYear || (expireYear == currentYear && expireMonth < currentMonth)) {
                HibernateConstraintValidatorContext context = constraintValidatorContext.unwrap(HibernateConstraintValidatorContext.class);
                context.addMessageParameter("expireMonth", expireMonth);
                context.addMessageParameter("expireYear", expireYear);
                context.buildConstraintViolationWithTemplate("Card has expired!! Expiry Date: {expireMonth}/{expireYear} is invalid !!").addConstraintViolation();
                return false;
            }
        }
        return true;
    }

}
