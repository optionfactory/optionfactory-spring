package net.optionfactory.spring.data.jpa.filtering.h2.reduction;

import net.optionfactory.spring.data.jpa.filtering.FilterRequest;

public interface ReductionNumberEntityRepository {
    Reduction reduce(FilterRequest request);

    public record Reduction(long count, long min, long max, double average) {
    }
}
