package net.optionfactory.spring.data.jpa.filtering.mysql;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Proxy;
import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.TextSearch;
import net.optionfactory.spring.data.jpa.filtering.filters.TextSearch.Syntax;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
import net.optionfactory.spring.data.jpa.test.containers.SharedContainer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Shows the mysql full-text search filter together with the FULLTEXT indexes
 * that serve it: the index must cover exactly the filter's paths, in the same
 * order. Unlike postgres, where a missing index degrades to a scan, mysql
 * refuses to run the query at all without one.
 *
 * Note the mysql-specific semantics: no stemming ("cats" does not match
 * "cat"), matching is case-folded by the column collation, and tokens shorter
 * than innodb_ft_min_token_size (3 by default) are not indexed.
 */
@SharedContainer(HibernateOnMysqlTestConfig.Mysql.class)
@SpringJUnitConfig(HibernateOnMysqlTestConfig.class)
@TransactionalPhases
public class TextSearchOnMysqlTest {

    @Entity
    @TextSearch(name = "byContent", paths = {"title", "body"}, language = "english")
    @TextSearch(name = "byFreeText", paths = {"title", "body"}, language = "english", syntax = Syntax.WEBSEARCH)
    @TextSearch(name = "byTitle", paths = "title", language = "italian", syntax = Syntax.PHRASE)
    public static class Article {

        @Id
        public long id;
        public String title;
        public String body;

    }

    public interface ArticlesRepository extends JpaRepository<Article, Long>, WhitelistFilteringRepository<Article> {

    }

    @Inject
    private ArticlesRepository articles;

    @PersistenceContext
    private EntityManager em;

    @Inject
    private EntityManagerFactory emf;

    @Test
    public void tooManyPathsFailAtFilterConstruction() {
        final var paths = new String[9];
        java.util.Arrays.fill(paths, "title");
        final var annotation = (TextSearch) Proxy.newProxyInstance(TextSearch.class.getClassLoader(), new Class<?>[]{TextSearch.class}, (proxy, method, args) -> switch (method.getName()) {
            case "name" ->
                "byContent";
            case "paths" ->
                paths;
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
        final var entity = emf.getMetamodel().entity(Article.class);
        final var thrown = Assertions.assertThrows(InvalidFilterConfiguration.class, () -> new TextSearch.TextSearchFilter(annotation, emf, entity));
        Assertions.assertTrue(thrown.getMessage().contains("at most 8 paths"), thrown.getMessage());
    }

    @BeforeEach
    public void setup() {
        articles.deleteAll();
        createFulltextIndexOnce("article_by_content_fts_idx", "(title, body)");
        createFulltextIndexOnce("article_by_title_fts_idx", "(title)");
        save(1, "The cats were running", "quickly and loudly");
        save(2, "Dogs bark", null);
        save(3, "Cani randagi", null);
        save(4, "Cani e gatti", null);
        save(5, "O'Brien's cats", null);
    }

    // mysql has no CREATE INDEX IF NOT EXISTS
    private void createFulltextIndexOnce(String name, String columns) {
        final var exists = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'text_search_on_mysql_test$article' AND index_name = ?
                """).setParameter(1, name).getSingleResult();
        if (exists.longValue() == 0) {
            em.createNativeQuery("CREATE FULLTEXT INDEX %s ON text_search_on_mysql_test$article %s".formatted(name, columns)).executeUpdate();
        }
    }

    private void save(long id, String title, String body) {
        final var a = new Article();
        a.id = id;
        a.title = title;
        a.body = body;
        articles.save(a);
    }

    private List<Long> search(String filter, String query) {
        final var fr = FilterRequest.builder()
                .textSearch(filter, query)
                .build();
        final Page<Article> page = articles.findAll(null, fr, Pageable.unpaged());
        return page.getContent().stream().map(a -> a.id).sorted().toList();
    }

    @Test
    public void plainMatchesWholeTermsAcrossAllPaths() {
        Assertions.assertEquals(List.of(1L), search("byContent", "cats running"));
        Assertions.assertEquals(List.of(1L), search("byContent", "running loudly"));
    }

    @Test
    public void plainFoldsCaseByCollation() {
        Assertions.assertEquals(List.of(1L), search("byContent", "CATS RUNNING"));
    }

    @Test
    public void plainDoesNotStem() {
        Assertions.assertEquals(List.of(), search("byContent", "cat runn"));
    }

    @Test
    public void plainRequiresEveryTerm() {
        Assertions.assertEquals(List.of(), search("byContent", "cats sheepdog"));
    }

    @Test
    public void websearchSupportsOrQuotedPhrasesAndNegation() {
        Assertions.assertEquals(List.of(1L, 2L, 5L), search("byFreeText", "cats OR dogs"));
        Assertions.assertEquals(List.of(5L), search("byFreeText", "cats -running"));
        Assertions.assertEquals(List.of(1L), search("byFreeText", "\"were running\""));
    }

    @Test
    public void phraseRequiresTermsToBeAdjacent() {
        Assertions.assertEquals(List.of(3L), search("byTitle", "cani randagi"));
        // 'e' is shorter than innodb_ft_min_token_size, but the gap still breaks adjacency
        Assertions.assertEquals(List.of(), search("byTitle", "cani gatti"));
    }

    @Test
    public void quotesInTheQueryAreEscaped() {
        Assertions.assertEquals(List.of(5L), search("byContent", "O'Brien's"));
    }

    @Test
    public void contentPredicateUsesTheFulltextIndex() {
        final var planText = explain("MATCH(title, body) AGAINST('cats running' IN BOOLEAN MODE)");
        Assertions.assertTrue(planText.contains("article_by_content_fts_idx"), planText);
    }

    @Test
    public void titlePredicateUsesTheFulltextIndex() {
        final var planText = explain("MATCH(title) AGAINST('\"cani randagi\"' IN BOOLEAN MODE)");
        Assertions.assertTrue(planText.contains("article_by_title_fts_idx"), planText);
    }

    private String explain(String predicate) {
        final List<?> rows = em.createNativeQuery("EXPLAIN SELECT id FROM text_search_on_mysql_test$article WHERE %s".formatted(predicate)).getResultList();
        return rows.stream().map(row -> java.util.Arrays.toString((Object[]) row)).collect(java.util.stream.Collectors.joining("\n"));
    }
}
