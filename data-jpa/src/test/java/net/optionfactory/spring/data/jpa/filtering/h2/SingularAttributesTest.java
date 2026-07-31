package net.optionfactory.spring.data.jpa.filtering.h2;

import jakarta.inject.Inject;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.InEnum;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import net.optionfactory.spring.data.jpa.filtering.PerMethodTransactional;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@PerMethodTransactional
public class SingularAttributesTest {

    @Entity
    @TextCompare(name = "performerName", path = "performer.name")
    @InEnum(name = "activitySeason", path = "activity.season", type = Activity.Season.class)
    @InEnum(name = "status", path = "status", type = Appointment.Status.class)
    public static class Appointment {

        @Id
        public long id;
        @Enumerated(EnumType.STRING)
        public Status status;
        @ManyToOne(cascade = CascadeType.ALL)
        public Activity activity;
        @ManyToOne(cascade = CascadeType.ALL)
        public Performer performer;

        public static enum Status {
            CONFIRMED, CANCELED, PENDING;
        }
    }

    @Entity
    public static class Performer {

        @Id
        public long id;
        public String name;
    }

    @Entity
    public static class Activity {

        @Id
        public long id;
        public String name;
        @Enumerated(EnumType.STRING)
        public Season season;

        public enum Season {
            SUMMER, WINTER;
        }
    }

    public interface AppointmentsRepository extends JpaRepository<Appointment, Long>, WhitelistFilteringRepository<Appointment> {

    }

    @Inject
    private AppointmentsRepository appointments;

    @BeforeEach
    public void setup() {
        final Activity swimming = activity(1, "swimming", Activity.Season.SUMMER);
        final Activity skying = activity(2, "skying", Activity.Season.WINTER);

        final Performer pietro = performer(1, "pietro");
        final Performer paolo = performer(2, "paolo");
        final Performer pietreppaolo = performer(3, "pietreppaolo");

        appointments.saveAll(Arrays.asList(
                appointment(1, skying, pietro, Appointment.Status.CONFIRMED),
                appointment(2, skying, paolo, Appointment.Status.CONFIRMED),
                appointment(3, skying, pietreppaolo, Appointment.Status.CANCELED),
                appointment(4, swimming, pietro, Appointment.Status.PENDING),
                appointment(5, swimming, paolo, Appointment.Status.PENDING),
                appointment(6, swimming, pietreppaolo, Appointment.Status.CONFIRMED)
        ));
    }

    @Test
    public void canFilterByComparingPerformerName() {
        final var fr = FilterRequest.builder()
                .text("performerName", f -> f.eq("pietro"))
                .build();
        final Pageable pr = Pageable.unpaged();
        final Page<Appointment> page = appointments.findAll(null, fr, pr);
        Assertions.assertEquals(Set.of(1L, 4L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByEmptyEnum() {
        final var fr = FilterRequest.builder()
                .inEnum("status")
                .build();
        final Pageable pr = Pageable.unpaged();
        final Page<Appointment> page = appointments.findAll(null, fr, pr);
        Assertions.assertTrue(page.isEmpty());
    }

    @Test
    public void canFilterByStatusInEnum() {
        final var fr = FilterRequest.builder()
                .inEnum("status", Appointment.Status.CONFIRMED)
                .build();

        final Pageable pr = Pageable.unpaged();
        final Page<Appointment> page = appointments.findAll(null, fr, pr);
        Assertions.assertEquals(Set.of(1L, 2L, 6L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByActivitySeasonInEnum() {
        final var fr = FilterRequest.builder()
                .inEnum("activitySeason", Activity.Season.SUMMER)
                .build();

        final Pageable pr = Pageable.unpaged();
        final Page<Appointment> page = appointments.findAll(null, fr, pr);
        Assertions.assertEquals(Set.of(4L, 5L, 6L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    private static Activity activity(long id, String name, Activity.Season season) {
        final Activity activity = new Activity();
        activity.id = id;
        activity.name = name;
        activity.season = season;
        return activity;
    }

    private static Performer performer(long id, String name) {
        final Performer performer = new Performer();
        performer.id = id;
        performer.name = name;
        return performer;
    }

    private static Appointment appointment(long id, Activity activity, Performer performer, Appointment.Status status) {
        final Appointment appointment = new Appointment();
        appointment.id = id;
        appointment.activity = activity;
        appointment.performer = performer;
        appointment.status = status;
        return appointment;
    }
}
