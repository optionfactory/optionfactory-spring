package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;

public class Sorters {

    public static Sort validateAndTransform(Class<?> root, Sort requested, Map<String, String> allowed) {
        return Sort.by(requested.get().map(o -> validateAndTransform(root, o, allowed)).collect(Collectors.toList()));
    }

    public static Order validateAndTransform(Class<?> root, Order o, Map<String, String> allowed) {
        final String name = o.getProperty();
        final String mapped = Optional
                .ofNullable(allowed.get(name))
                .orElseThrow(() -> new InvalidSortRequest(name, root, "sorter not configured in root object"));
        return o.withProperty(mapped);
    }

}
