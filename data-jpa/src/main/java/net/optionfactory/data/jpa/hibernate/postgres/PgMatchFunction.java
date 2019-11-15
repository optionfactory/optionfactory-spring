package net.optionfactory.data.jpa.hibernate.postgres;

import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.QueryException;
import org.hibernate.dialect.function.SQLFunction;
import org.hibernate.engine.spi.Mapping;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.BooleanType;
import org.hibernate.type.Type;

public class PgMatchFunction implements SQLFunction {

    @Override
    public Type getReturnType(Type columnType, Mapping mapping) throws QueryException {
        return new BooleanType();
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public boolean hasParenthesesIfNoArguments() {
        return false;
    }

    @Override
    public String render(Type type, List args, SessionFactoryImplementor factory) throws QueryException {
        final String aliases = ((List<String>) args).subList(0, args.size() - 1).stream().collect(Collectors.joining(", "));
        final String query = (String) args.get(args.size() - 1);
        return String.format("to_tsvector('english', concat_ws(' ', %s)) @@ plainto_tsquery('english', %s )", aliases, query);
    }

}
