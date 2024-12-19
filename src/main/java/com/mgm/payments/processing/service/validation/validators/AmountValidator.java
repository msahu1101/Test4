package com.mgm.payments.processing.service.validation.validators;

import com.mgm.payments.processing.service.model.Amount;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.util.List;

public class AmountValidator implements ConstraintValidator<AmountConstraint, List<Amount>> {

    @Override
    public boolean isValid(List<Amount> amounts, ConstraintValidatorContext constraintValidatorContext) {
        return !(amounts == null || amounts.isEmpty() || amounts.stream().noneMatch(o -> o.getName().equals("total") &&
                o.getValue() != null && o.getValue().compareTo(BigDecimal.ZERO) >= 0
        && ((o.getValue().precision()-o.getValue().scale()) <= 9)));


    }


}

