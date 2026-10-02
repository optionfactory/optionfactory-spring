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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class StrictEmailTest {

    private static final Validator validator = Validation.byDefaultProvider().configure()
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
                Arguments.of("x@example.com"),
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
                Arguments.of("", "an empty string is no address"),
                Arguments.of(" ", "a blank string is no address"),
                Arguments.of(" email@example.com", "leading whitespace is not trimmed"),
                Arguments.of("email@example.com ", "trailing whitespace is not trimmed"),
                Arguments.of("test@1", "a domain without a dot"),
                Arguments.of("test@tld", "a domain without a dot"),
                Arguments.of("@", "neither local part nor domain"),
                Arguments.of("asd@", "no domain"),
                Arguments.of("@asd", "no local part"),
                Arguments.of(".test@example.com", "a local part starting with a dot"),
                Arguments.of("test.@example.com", "a local part ending with a dot"),
                Arguments.of("test..test@example.com", "consecutive dots in the local part"),
                Arguments.of("Abc.example.com", "no @ character"),
                Arguments.of("A@b@c@example.com", "only one @ is allowed outside quotation marks"),
                Arguments.of("\"(),:;<>[\\]@example.com", "none of the special characters in this local-part are allowed outside quotation marks"),
                Arguments.of("just\"not\"right@example.com", "quoted strings must be dot separated or the only element making up the local-part"),
                Arguments.of("this is\"not\\allowed@example.com", "spaces, quotes, and backslashes may only exist when within quoted strings and preceded by a backslash"),
                Arguments.of("this\\ still\\\"not\\\\allowed@example.com", "even if escaped (preceded by a backslash), spaces, quotes, and backslashes must still be contained by quotes"),
                Arguments.of("1234567890123456789012345678901234567890123456789012345678901234+x@example.com", "local part is longer than 64 characters"),
                Arguments.of("email@[123.123.123.123]", "an ip literal domain is valid, but not accepted"),
                Arguments.of("email@127.0.0.1", "an ip address domain is valid, but not accepted"),
                Arguments.of("admin@mailserver1", "a local domain name with no TLD is valid, but not accepted: ICANN highly discourages dotless email addresses"),
                Arguments.of("\" \"@example.com", "a quoted space is valid, but not accepted"),
                Arguments.of("\"john..doe\"@example.com", "a quoted double dot is valid, but not accepted"),
                Arguments.of("mailhost!username@example.com", "a bangified host route used for uucp mailers is valid, but not accepted"),
                Arguments.of("user%example.com@example.com", "a % escaped mail route to user@example.com via example.org is valid, but not accepted"),
                Arguments.of("test@example", "top level domain only"),
                Arguments.of("test@example.c", "single character top level domain"),
                Arguments.of("test@0-0o_.com", "an underscore in the domain")
        );
    }

    @ParameterizedTest
    @MethodSource("accepted")
    public void emailAddressAreAccepted(String email) {
        final var bean = new BeanWithEmail(email);
        final var violations = validator.validate(bean);
        Assertions.assertEquals(Set.of(), violations, "'%s' must be accepted".formatted(email));
    }

    @ParameterizedTest
    @MethodSource("rejected")
    public void emailAddressAreRejected(String email, String reason) {
        final var bean = new BeanWithEmail(email);
        final Set<ConstraintViolation<BeanWithEmail>> violations = validator.validate(bean);
        Assertions.assertEquals(List.of("Specificare un indirizzo email valido"), violations.stream().map(cv -> cv.getMessage()).toList(), "'%s' must be rejected with exactly one violation: %s".formatted(email, reason));
    }

    @Test
    public void aCustomMessageReachesTheViolation() {
        final var violations = validator.validate(new BeanWithCustomMessage("not an email"));
        Assertions.assertEquals(List.of("custom message"), violations.stream().map(cv -> cv.getMessage()).toList(), "the message given on @StrictEmail must be the violation's message");
    }

    public static record BeanWithEmail(@StrictEmail String email) {

    }

    public static record BeanWithCustomMessage(@StrictEmail(message = "custom message") String email) {

    }
}
