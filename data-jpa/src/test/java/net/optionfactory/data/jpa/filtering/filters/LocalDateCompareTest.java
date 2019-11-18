package net.optionfactory.data.jpa.filtering.filters;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.data.jpa.HibernateTestConfig;
import net.optionfactory.data.jpa.filtering.Appointment;
import net.optionfactory.data.jpa.filtering.AppointmentsRepository;
import net.optionfactory.data.jpa.filtering.FilterRequest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = HibernateTestConfig.class)
@Transactional
public class LocalDateCompareTest {

    @Autowired
    private AppointmentsRepository appointments;

    @Before
    public void setup() {
        appointments.saveAll(Arrays.asList(
                appointment(1, LocalDate.parse("2019-01-10")),
                appointment(2, LocalDate.parse("2019-01-11")),
                appointment(3, LocalDate.parse("2019-01-11")),
                appointment(4, LocalDate.parse("2019-02-25")),
                appointment(5, LocalDate.parse("2019-10-01"))
        ));
    }

    @Test
    public void canFilterByLocalDateEquality() {
        final Page<Appointment> page = appointments.findAll(filter(LocalDateCompare.Operator.EQ, "2019-01-11"), Pageable.unpaged());
        Assert.assertEquals(new HashSet<>(Arrays.asList(2L, 3L)), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByLocalDateLessThan() {
        final Page<Appointment> page = appointments.findAll(filter(LocalDateCompare.Operator.LT, "2019-01-11"), Pageable.unpaged());
        Assert.assertEquals(new HashSet<>(Arrays.asList(1L)), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByLocalDateGreaterThan() {
        final Page<Appointment> page = appointments.findAll(filter(LocalDateCompare.Operator.GT, "2019-01-11"), Pageable.unpaged());
        Assert.assertEquals(new HashSet<>(Arrays.asList(4L, 5L)), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByLocalDateLessThanOrEqualTo() {
        final Page<Appointment> page = appointments.findAll(filter(LocalDateCompare.Operator.LTE, "2019-01-11"), Pageable.unpaged());
        Assert.assertEquals(new HashSet<>(Arrays.asList(1L, 2L, 3L)), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByLocalDateGreaterThanOrEqualTo() {
        final Page<Appointment> page = appointments.findAll(filter(LocalDateCompare.Operator.GTE, "2019-01-11"), Pageable.unpaged());
        Assert.assertEquals(new HashSet<>(Arrays.asList(2L, 3L, 4L, 5L)), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByLocalDateBetween() {
        final Page<Appointment> page = appointments.findAll(filter(LocalDateCompare.Operator.BETWEEN, "2019-01-11", "2019-09-30"), Pageable.unpaged());
        Assert.assertEquals(new HashSet<>(Arrays.asList(2L, 3L, 4L)), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    private static FilterRequest filter(LocalDateCompare.Operator operator, String... values) {
        final FilterRequest fr = new FilterRequest();
        fr.put("date", Stream.concat(Stream.of(operator.name()), Stream.of(values)).toArray(i -> new String[i]));
        return fr;
    }

    private static Appointment appointment(long id, LocalDate date) {
        final Appointment appointment = new Appointment();
        appointment.id = id;
        appointment.created = Instant.EPOCH;
        appointment.date = date;
        appointment.activity = null;
        appointment.performer = null;
        appointment.status = Appointment.Status.PENDING;
        return appointment;
    }
}
