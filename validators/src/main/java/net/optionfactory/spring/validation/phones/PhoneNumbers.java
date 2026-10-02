package net.optionfactory.spring.validation.phones;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber;
import java.util.Optional;
import java.util.Set;

/// Phone number validation and formatting on top of google's libphonenumber, as used by
/// [PhoneNumber] and usable on its own.
public class PhoneNumbers {

    private static final PhoneNumberUtil PHONES = PhoneNumberUtil.getInstance();

    /// Why a phone number was rejected.
    public enum PhoneValidationError {
        /// The value is empty, cannot be parsed, or is not a valid number for its region.
        INVALID_NUMBER,
        /// The value is a valid number, of a type that is not accepted.
        INVALID_TYPE
    }

    /// Validates a phone number and checks its type.
    ///
    /// @param phoneNumber the number, with or without an international prefix
    /// @param types the accepted types; a number is accepted only when libphonenumber classifies it
    ///        as exactly one of them, so `FIXED_LINE_OR_MOBILE` does not admit a number classified
    ///        as `MOBILE`
    /// @param defaultRegion the two-letter region code numbers without an international prefix are read in
    /// @return empty when the number is valid and of an accepted type, the reason it is not otherwise;
    ///         a `null` or empty number is an `INVALID_NUMBER`
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

    /// Formats a phone number in E.164, `+` and digits only (`+393331234567`), the canonical form
    /// to store and compare numbers in.
    ///
    /// The number is parsed, not validated: a number too short to exist is formatted all the same,
    /// so validate first.
    ///
    /// @param phoneNumber the number, with or without an international prefix
    /// @param defaultRegion the two-letter region code a number without an international prefix is read in
    /// @return the number in E.164 format, or an empty string when `phoneNumber` is `null` or empty
    /// @throws IllegalArgumentException when the number cannot be parsed at all
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
