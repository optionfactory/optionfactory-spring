package net.optionfactory.spring.validation.phones;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber;
import java.util.EnumSet;
import java.util.List;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    private static final PhoneNumberUtil PHONES = PhoneNumberUtil.getInstance();
    private EnumSet<PhoneNumberType> types;
    private String defaultRegion;

    @Override
    public void initialize(PhoneNumber annotation) {
        this.types = EnumSet.copyOf(List.of(annotation.types()));
        this.defaultRegion = annotation.defaultRegion();
    }

    @Override
    public boolean isValid(String phoneNumber, ConstraintValidatorContext ctx) {
        if(phoneNumber == null){
            return true;
        }
        if (phoneNumber.isEmpty()) {
            return false;
        }
        try {
            final Phonenumber.PhoneNumber parsed = PHONES.parse(phoneNumber, defaultRegion);
            if (!PHONES.isValidNumber(parsed)) {
                return false;
            }
            final PhoneNumberUtil.PhoneNumberType type = PHONES.getNumberType(parsed);
            return types.contains(type);
        } catch (NumberParseException ex) {
            return false;
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
