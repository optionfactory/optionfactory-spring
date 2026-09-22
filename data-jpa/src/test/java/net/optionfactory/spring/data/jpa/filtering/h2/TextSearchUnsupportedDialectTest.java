package net.optionfactory.spring.data.jpa.filtering.h2;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.lang.reflect.Proxy;
import net.optionfactory.spring.data.jpa.filtering.filters.TextSearch;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration;
import net.optionfactory.spring.data.jpa.filtering.h2.filters.TextCompareTest.Root;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
public class TextSearchUnsupportedDialectTest {

    @Inject
    private EntityManagerFactory emf;

    @Test
    public void unsupportedDialectFailsAtFilterConstruction() {
        final var annotation = (TextSearch) Proxy.newProxyInstance(TextSearch.class.getClassLoader(), new Class<?>[]{TextSearch.class}, (proxy, method, args) -> switch (method.getName()) {
            case "name" ->
                "byContent";
            case "paths" ->
                new String[]{"name"};
            case "language" ->
                "simple";
            case "syntax" ->
                TextSearch.Syntax.PLAIN;
            case "annotationType" ->
                TextSearch.class;
            case "toString" ->
                "@TextSearch(proxy)";
            case "hashCode" ->
                System.identityHashCode(proxy);
            case "equals" ->
                proxy == args[0];
            default ->
                null;
        });
        final EntityType<?> entity = emf.getMetamodel().entity(Root.class);
        final var thrown = Assertions.assertThrows(InvalidFilterConfiguration.class, () -> new TextSearch.TextSearchFilter(annotation, emf, entity));
        Assertions.assertTrue(thrown.getMessage().contains("requires postgres or mysql"), thrown.getMessage());
    }
}
