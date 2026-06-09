package net.optionfactory.spring.validation.phones;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber;
import java.util.Optional;
import java.util.Set;

public class PhoneNumbers {

    private static final PhoneNumberUtil PHONES = PhoneNumberUtil.getInstance();
    
    public enum PhoneValidationError {
        INVALID_NUMBER,
        INVALID_TYPE
    }


    public static Optional<PhoneValidationError> validate(String phoneNumber, Set<PhoneNumberType> types, String defaultRegion) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return Optional.of(PhoneValidationError.INVALID_NUMBER);
        }
        try {
            final Phonenumber.PhoneNumber parsed = PHONES.parse(phoneNumber, defaultRegion);
            if (!PHONES.isValidNumber(parsed)) {
                return Optional.of(PhoneValidationError.INVALID_NUMBER);
            }
            final PhoneNumberUtil.PhoneNumberType type = PHONES.getNumberType(parsed);
            return types.contains(type) ? Optional.empty() : Optional.of(PhoneValidationError.INVALID_TYPE);
        } catch (NumberParseException ex) {
            return Optional.of(PhoneValidationError.INVALID_NUMBER);
        }
    }    
    
    public static String e164Format(String phoneNumber, String defaultRegion) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return "";
        }
        try {
            final Phonenumber.PhoneNumber parsed = PHONES.parse(phoneNumber, defaultRegion);
            return PHONES.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
}
