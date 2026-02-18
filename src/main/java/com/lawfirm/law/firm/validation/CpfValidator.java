package com.lawfirm.law.firm.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CpfValidator implements ConstraintValidator<ValidCPF, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return false;
        String digits = value.replaceAll("\\D", "");
        if (digits.length() != 11) return false;

        // Reject known invalid sequences
        if (digits.matches("^(\\d)\\1{10}$")) return false;

        try {
            int sum = 0;
            for (int i = 0; i < 9; i++) {
                sum += Character.getNumericValue(digits.charAt(i)) * (10 - i);
            }
            int firstCheck = 11 - (sum % 11);
            if (firstCheck >= 10) firstCheck = 0;

            if (firstCheck != Character.getNumericValue(digits.charAt(9))) return false;

            sum = 0;
            for (int i = 0; i < 10; i++) {
                sum += Character.getNumericValue(digits.charAt(i)) * (11 - i);
            }
            int secondCheck = 11 - (sum % 11);
            if (secondCheck >= 10) secondCheck = 0;

            return secondCheck == Character.getNumericValue(digits.charAt(10));
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
