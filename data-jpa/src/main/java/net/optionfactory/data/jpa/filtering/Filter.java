package net.optionfactory.data.jpa.filtering;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

public interface Filter {

    String name();

    Predicate toPredicate(CriteriaBuilder builder, Root<?> root, String[] values);
}
