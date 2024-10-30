package net.optionfactory.spring.marshaling.jaxb.money;

import org.junit.Assert;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class XsdDecimalToLongCentsTest {

    private final XsdDecimalToLongCents adapter = new XsdDecimalToLongCents();

    @DataPoints("unmarshal")
    public static final UnmarshalInputAndExpectation[] UNMARSHALING_DATAPOINTS = {
        new UnmarshalInputAndExpectation(null, null),
        new UnmarshalInputAndExpectation("0", 0L),
        new UnmarshalInputAndExpectation("1", 100L),
        new UnmarshalInputAndExpectation("0.1", 10L),
        new UnmarshalInputAndExpectation("0.01", 1L),
        new UnmarshalInputAndExpectation("0.001", 0L),
        new UnmarshalInputAndExpectation("123", 12300L),
        new UnmarshalInputAndExpectation("123.4", 12340L),
        new UnmarshalInputAndExpectation("123.40", 12340L),
        new UnmarshalInputAndExpectation("123.04", 12304L),
        new UnmarshalInputAndExpectation("12345.06", 1234506L)
    };

    @Theory
    public void unmarshal(@FromDataPoints("unmarshal") final UnmarshalInputAndExpectation iae) {
        Assert.assertEquals(iae.expected, adapter.unmarshal(iae.input));
    }

    @DataPoints("marshal")
    public static final MarshalInputAndExpectation[] MARSHALING_DATAPOINTS = {
        new MarshalInputAndExpectation(null, null),
        new MarshalInputAndExpectation(0L, "0"),
        new MarshalInputAndExpectation(1L, "0.01"),
        new MarshalInputAndExpectation(10L, "0.1"),
        new MarshalInputAndExpectation(100L, "1"),
        new MarshalInputAndExpectation(12300L, "123"),
        new MarshalInputAndExpectation(12340L, "123.4"),
        new MarshalInputAndExpectation(12304L, "123.04"),
        new MarshalInputAndExpectation(1234506L, "12345.06")
    };

    @Theory
    public void marshal(@FromDataPoints("marshal") final MarshalInputAndExpectation iae) {
        Assert.assertEquals(iae.expected, adapter.marshal(iae.input));
    }

    public static class UnmarshalInputAndExpectation {

        public final String input;
        public final Long expected;

        public UnmarshalInputAndExpectation(String input, Long expected) {
            this.input = input;
            this.expected = expected;
        }

    }

    public static class MarshalInputAndExpectation {

        public final Long input;
        public final String expected;

        public MarshalInputAndExpectation(Long input, String expected) {
            this.input = input;
            this.expected = expected;
        }

    }

}
