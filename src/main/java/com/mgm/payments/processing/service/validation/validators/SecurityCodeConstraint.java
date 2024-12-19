package com.mgm.payments.processing.service.validation.validators;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = SecurityCodeValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SecurityCodeConstraint {
    String message() default "Security Code Error";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}