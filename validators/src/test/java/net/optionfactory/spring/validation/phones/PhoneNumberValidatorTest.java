package net.optionfactory.spring.validation.phones;

import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class PhoneNumberValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    public record DefaultPhoneBean(@PhoneNumber String phone) {

    }

    public record UsRegionPhoneBean(@PhoneNumber(defaultRegion = "US") String phone) {

    }

    public record TollFreePhoneBean(@PhoneNumber(types = PhoneNumberType.TOLL_FREE) String phone) {

    }

    public static Stream<Arguments> phoneData() {
        final String errInvalidNumber = "{jakarta.validation.constraints.PhoneNumber.INVALID_NUMBER.message}";
        final String errInvalidType = "{jakarta.validation.constraints.PhoneNumber.INVALID_TYPE.message}";

        return Stream.of(
                Arguments.of(DefaultPhoneBean.class, null, true, null, "Null is handled by @NotNull, not constraint validators"),
                Arguments.of(DefaultPhoneBean.class, "", false, errInvalidNumber, "Empty string is explicitly evaluated as INVALID_NUMBER"),
                Arguments.of(DefaultPhoneBean.class, "   ", false, errInvalidNumber, "Blank string fails parsing"),
                Arguments.of(DefaultPhoneBean.class, "3331234567", true, null, "Valid Italian mobile number without prefix (implied IT)"),
                Arguments.of(DefaultPhoneBean.class, "021234567", false, errInvalidType, "Valid Italian fixed line exluded by type filter"),
                Arguments.of(DefaultPhoneBean.class, "+393331234567", true, null, "Valid explicit Italian mobile number (+39)"),
                Arguments.of(DefaultPhoneBean.class, "+12025550156", true, null, "Explicit US prefix (+1) successfully overrides the default IT region"),
                Arguments.of(DefaultPhoneBean.class, "12345", false, errInvalidNumber, "Number is too short to be valid"),
                Arguments.of(DefaultPhoneBean.class, "not-a-number", false, errInvalidNumber, "Unparseable garbage string"),
                Arguments.of(DefaultPhoneBean.class, "800123456", false, errInvalidType, "Valid Italian toll-free number, but bean only accepts Mobile/Fixed"),
                Arguments.of(UsRegionPhoneBean.class, "2025550156", true, null, "Valid US number without prefix (implied US)"),
                Arguments.of(UsRegionPhoneBean.class, "3331234567", false, errInvalidNumber, "Italian implicit number parsed using US rules fails"),
                Arguments.of(TollFreePhoneBean.class, "800123456", true, null, "Valid Italian toll-free number matches specific allowed type"),
                Arguments.of(TollFreePhoneBean.class, "3331234567", false, errInvalidType, "Valid mobile number rejected because bean expects TOLL_FREE")
        );
    }

    @ParameterizedTest(name = "[{index}] {4}: ''{1}'' in {0} -> valid: {2}")
    @MethodSource("phoneData")
    public void canValidatePhoneNumber(Class<?> beanClass, String phone, boolean expectedValid, String expectedErrorTemplate, String reason) throws Exception {
        final Object targetBean = beanClass.getDeclaredConstructor(String.class).newInstance(phone);
        final var violations = validator.validate(targetBean);
        final var isValid = violations.isEmpty();
        Assertions.assertEquals(expectedValid, isValid, String.format("Test Failed: %s. Expected valid=%s for phone '%s'", reason, expectedValid, phone));

        if (!expectedValid && expectedErrorTemplate != null) {
            final var actualTemplate = violations.iterator().next().getMessageTemplate();
            Assertions.assertEquals(expectedErrorTemplate, actualTemplate, String.format("Test Failed: %s. Expected violation template '%s' but got '%s'", reason, expectedErrorTemplate, actualTemplate));
        }
    }
}
