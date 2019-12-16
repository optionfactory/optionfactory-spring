package net.optionfactory.data.jpa.filtering.filters.spi;

public class InvalidFilterRequest extends IllegalArgumentException {

    public InvalidFilterRequest(String reason) {
        super(reason);
    }
}
