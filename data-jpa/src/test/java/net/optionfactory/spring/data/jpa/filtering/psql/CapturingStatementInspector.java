package net.optionfactory.spring.data.jpa.filtering.psql;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Captures the SQL Hibernate sends while the given action runs, on the current
 * thread: used to EXPLAIN the statements the criteria-built predicates really
 * render, instead of a hand-rewritten lookalike.
 */
public class CapturingStatementInspector implements StatementInspector {

    private static final ThreadLocal<List<String>> CAPTURE = new ThreadLocal<>();

    public static <T> List<String> capture(Supplier<T> action) {
        CAPTURE.set(new ArrayList<>());
        try {
            action.get();
            return List.copyOf(CAPTURE.get());
        } finally {
            CAPTURE.remove();
        }
    }

    @Override
    public String inspect(String sql) {
        final var captured = CAPTURE.get();
        if (captured != null) {
            captured.add(sql);
        }
        return sql;
    }

}
