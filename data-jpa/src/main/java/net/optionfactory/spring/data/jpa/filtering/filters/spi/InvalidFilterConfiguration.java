package net.optionfactory.spring.data.jpa.filtering.filters.spi;

public class InvalidFilterConfiguration extends IllegalStateException {

    public InvalidFilterConfiguration(String reason) {
        super(reason);
    }
}
