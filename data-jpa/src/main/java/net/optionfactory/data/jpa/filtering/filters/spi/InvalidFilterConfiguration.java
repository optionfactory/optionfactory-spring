package net.optionfactory.data.jpa.filtering.filters.spi;

public class InvalidFilterConfiguration extends IllegalStateException {

    public InvalidFilterConfiguration(String reason) {
        super(reason);
    }
}
