package net.optionfactory.spring.data.jpa.filtering.h2.filters.instant;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.InstantCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.InstantCompare.Operator;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = HibernateOnH2TestConfig.class)
@Transactional
public class InstantCompareTest {

    @Autowired
    private EntityForInstantRepository repo;

    @Before
    public void setup() {
        repo.saveAll(Arrays.asList(
                entity(1, Instant.EPOCH),
                entity(2, Instant.ofEpochSecond(0, 1_000)),
                entity(3, Instant.ofEpochMilli(1_000)),
                entity(4, Instant.ofEpochSecond(1_000)),
                entity(5, Instant.parse("1970-01-01T12:00:00.123Z")),
                entity(6, Instant.parse("1970-01-01T12:00:00.123456Z")),
                entity(7, null)
        ));
    }

    @Test
    public void canFilterInstantsFromIsoInstant() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantIso", InstantCompare.Operator.GTE, "1970-01-01T00:00:01Z"), Pageable.unpaged());
        Assert.assertEquals(Set.of(3L, 4L, 5L, 6L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterInstantsFromUnixSecond() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantUnixS", InstantCompare.Operator.GTE, "1000"), Pageable.unpaged());
        Assert.assertEquals(Set.of(4L, 5L, 6L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterInstantsFromUnixMillisecond() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantUnixMS", InstantCompare.Operator.GTE, "1000"), Pageable.unpaged());
        Assert.assertEquals(Set.of(3L, 4L, 5L, 6L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterInstantsFromUnixNanosecond() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantUnixNS", InstantCompare.Operator.GTE, "1000"), Pageable.unpaged());
        Assert.assertEquals(Set.of(2L, 3L, 4L, 5L, 6L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterInstantsBeforeIsoInstant() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantIso", InstantCompare.Operator.LT, "1970-01-01T00:00:01Z"), Pageable.unpaged());
        Assert.assertEquals(Set.of(1L, 2L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterInstantsBeforeUnixSecond() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantUnixS", InstantCompare.Operator.LT, "1000"), Pageable.unpaged());
        Assert.assertEquals(Set.of(1L, 2L, 3L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterInstantsBeforeUnixMillisecond() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantUnixMS", InstantCompare.Operator.LT, "1000"), Pageable.unpaged());
        Assert.assertEquals(Set.of(1L, 2L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterInstantsBeforeUnixNanosecond() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantUnixNS", InstantCompare.Operator.LT, "1000"), Pageable.unpaged());
        Assert.assertEquals(Set.of(1L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterInstantsBetweenIsoInstant() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantIso", InstantCompare.Operator.BETWEEN, "1970-01-01T00:00:00Z", "1970-01-01T00:00:01Z"), Pageable.unpaged());
        Assert.assertEquals(Set.of(1L, 2L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterInstantsBetweenUnixSecond() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantUnixS", InstantCompare.Operator.BETWEEN, "0", "1000"), Pageable.unpaged());
        Assert.assertEquals(Set.of(1L, 2L, 3L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterInstantsBetweenUnixMillisecond() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantUnixMS", InstantCompare.Operator.BETWEEN, "0", "1000"), Pageable.unpaged());
        Assert.assertEquals(Set.of(1L, 2L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterInstantsBetweenUnixNanosecond() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantUnixNS", InstantCompare.Operator.BETWEEN, "0", "1000"), Pageable.unpaged());
        Assert.assertEquals(Set.of(1L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void filterFromIsInclusiveOnInputWithLessPrecision() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantIso", InstantCompare.Operator.GTE, "1970-01-01T12:00:00.123Z"), Pageable.unpaged());
        Assert.assertEquals(Set.of(5L, 6L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void filterBeforeIsExclusiveOnInputWithLessPrecision() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantIso", InstantCompare.Operator.LT, "1970-01-01T12:00:00.123Z"), Pageable.unpaged());
        Assert.assertEquals(Set.of(1L, 2L, 3L, 4L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void filterBetweenIsLeftInclusiveOnInputWithLessPrecision() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantIso", InstantCompare.Operator.BETWEEN, "1970-01-01T12:00:00.123Z", "1970-01-01T12:00:00.124Z"), Pageable.unpaged());
        Assert.assertEquals(Set.of(5L, 6L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void filterBetweenIsRightExclusiveOnInputWithLessPrecision() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantIso", InstantCompare.Operator.BETWEEN, "1970-01-01T12:00:00.122Z", "1970-01-01T12:00:00.123Z"), Pageable.unpaged());
        Assert.assertEquals(Set.<Long>of(), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void filterFromIsInclusiveOnInputWithMorePrecision() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantIso", InstantCompare.Operator.GTE, "1970-01-01T12:00:00.123456Z"), Pageable.unpaged());
        Assert.assertEquals(Set.of(6L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void filterBeforeIsExclusiveOnInputWithMorePrecision() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantIso", InstantCompare.Operator.LT, "1970-01-01T12:00:00.123456Z"), Pageable.unpaged());
        Assert.assertEquals(Set.of(1L, 2L, 3L, 4L, 5L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void filterBetweenIsRightExclusiveOnInputWithMorePrecision() {
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantIso", InstantCompare.Operator.BETWEEN, "1970-01-01T12:00:00.123455Z", "1970-01-01T12:00:00.123456Z"), Pageable.unpaged());
        Assert.assertEquals(Set.<Long>of(), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void filteringWithNeqIncludesNullValues() {
        final Page<EntityForInstant> all = repo.findAll(Pageable.unpaged());
        final Page<EntityForInstant> page = repo.findAll(null, filter("instantIso", Operator.NEQ, "2222-02-02T02:02:02.222Z"), Pageable.unpaged());
        Assert.assertEquals(all.getTotalElements(), page.getTotalElements());
    }

    private static FilterRequest filter(String filterName, InstantCompare.Operator operator, String... values) {
        return FilterRequest.of(Map.of(filterName, Stream.concat(Stream.of(operator.name()), Stream.of(values)).toArray(i -> new String[i])));
    }

    private static EntityForInstant entity(long id, Instant instant) {
        final EntityForInstant e = new EntityForInstant();
        e.id = id;
        e.instant = instant;
        return e;
    }
}
