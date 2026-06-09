package net.optionfactory.spring.validation.email;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import net.optionfactory.spring.validation.emails.StrictEmail;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class StrictEmailTest {

    private final Validator validator = Validation.byDefaultProvider().configure()
            .messageInterpolator(new ParameterMessageInterpolator(Set.of(), Locale.ITALIAN, false))
            .buildValidatorFactory()
            .getValidator();

    public static Stream<Arguments> accepted() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of("test@example.com"),
                Arguments.of("test@example.co"),
                Arguments.of("test.test@example.com"),
                Arguments.of("test.test.test@example.com"),
                Arguments.of("test+test@example.com"),
                Arguments.of("test+test+test@example.com"),
                Arguments.of("test-test-test@example.com"),
                Arguments.of("x@example.com"), // one-letter local-part
                Arguments.of("_@example.com"),
                Arguments.of("test@example-example.com"),
                Arguments.of("test@x.example"),
                Arguments.of("test@x123.com"),
                Arguments.of("test@0-0-0o.com"),
                Arguments.of("test@0-wh-ao14-0.com-com.net"),
                Arguments.of("test@a-1234567890-1234567890-1234567890-1234567890-1234567890-1234-z.eu.us"),
                Arguments.of("test@xn--d1ai6ai.xn--p1ai"),
                Arguments.of("someone.-85@hotmail.it")
        );
    }

    public static Stream<Arguments> rejected() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of(" "),
                Arguments.of(" email@example.com"),
                Arguments.of("email@example.com "),
                Arguments.of("test@1"),
                Arguments.of("test@tld"),
                Arguments.of("@"),
                Arguments.of("asd@"),
                Arguments.of("@asd"),
                Arguments.of(".test@example.com"),
                Arguments.of("test.@example.com"),
                Arguments.of("test..test@example.com"),
                Arguments.of("Abc.example.com"), // no @ character
                Arguments.of("A@b@c@example.com"), // only one @ is allowed outside quotation marks
                Arguments.of("\"(),:;<>[\\]@example.com"), // none of the special characters in this local-part are allowed outside quotation marks
                Arguments.of("just\"not\"right@example.com"), // quoted strings must be dot separated or the only element making up the local-part
                Arguments.of("this is\"not\\allowed@example.com"), // spaces, quotes, and backslashes may only exist when within quoted strings and preceded by a backslash
                Arguments.of("this\\ still\\\"not\\\\allowed@example.com"), // even if escaped (preceded by a backslash), spaces, quotes, and backslashes must still be contained by quotes
                Arguments.of("1234567890123456789012345678901234567890123456789012345678901234+x@example.com"), // local part is longer than 64 characters
                //  the following are still valid, but not accepted
                Arguments.of("email@[123.123.123.123]"), // valid, but not accepted
                Arguments.of("email@127.0.0.1"),
                Arguments.of("admin@mailserver1"), // local domain name with no TLD, although ICANN highly discourages dotless email addresses
                Arguments.of("\" \"@example.com"), // space between the quotes
                Arguments.of("\"john..doe\"@example.com"), // quoted double dot
                Arguments.of("mailhost!username@example.com"), // bangified host route used for uucp mailers
                Arguments.of("user%example.com@example.com"), // % escaped mail route to user@example.com via example.org
                Arguments.of("test@example"), // top level domain only
                Arguments.of("test@example.c"), // single character domain
                Arguments.of("test@0-0o_.com")
        );
    }

    @ParameterizedTest
    @MethodSource("accepted")
    public void emailAddressAreAccepted(String email) {
        final var bean = new BeanWithEmail(email);
        final var violations = validator.validate(bean);
        Assertions.assertEquals(Set.of(), violations);
    }

    @ParameterizedTest
    @MethodSource("rejected")
    public void emailAddressAreRejected(String email) {
        final var bean = new BeanWithEmail(email);
        final Set<ConstraintViolation<BeanWithEmail>> violations = validator.validate(bean);
        Assertions.assertEquals(List.of("Specificare un indirizzo email valido"), violations.stream().map(cv -> cv.getMessage()).toList());
    }

    public static record BeanWithEmail(@StrictEmail String email) {

    }
}
