package net.optionfactory.spring.data.jpa.filtering.h2.chaining;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
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
public class PropertyChainTest {

    @Autowired
    private ActivitiesRepository activities;
    @Autowired
    private PerformersRepository performers;
    @Autowired
    private AppointmentsRepository appointments;

    @Before
    public void setup() {
        final Activity swimming = activities.save(activity(1, "swimming", Activity.Season.SUMMER));
        final Activity skying = activities.save(activity(2, "skying", Activity.Season.WINTER));

        final Performer pietro = performers.save(performer(1, "pietro"));
        final Performer paolo = performers.save(performer(2, "paolo"));
        final Performer pietreppaolo = performers.save(performer(3, "pietreppaolo"));

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
        Assert.assertEquals(Set.of(1L, 4L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByEmptyEnum() {
        final var fr = FilterRequest.builder()
                .inEnum("status")
                .build();
        final Pageable pr = Pageable.unpaged();
        final Page<Appointment> page = appointments.findAll(null, fr, pr);
        Assert.assertTrue(page.isEmpty());
    }

    @Test
    public void canFilterByStatusInEnum() {
        final var fr = FilterRequest.builder()
                .inEnum("status", Appointment.Status.CONFIRMED)
                .build();
        
        final Pageable pr = Pageable.unpaged();
        final Page<Appointment> page = appointments.findAll(null, fr, pr);
        Assert.assertEquals(Set.of(1L, 2L, 6L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByActivitySeasonInEnum() {
        final var fr = FilterRequest.builder()
                .inEnum("activitySeason", Activity.Season.SUMMER)
                .build();
        
        final Pageable pr = Pageable.unpaged();
        final Page<Appointment> page = appointments.findAll(null, fr, pr);
        Assert.assertEquals(Set.of(4L, 5L, 6L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
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
