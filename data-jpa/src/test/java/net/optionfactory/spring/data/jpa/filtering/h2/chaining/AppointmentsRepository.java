package net.optionfactory.spring.data.jpa.filtering.h2.chaining;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentsRepository extends JpaRepository<Appointment, Long>, WhitelistFilteringRepository<Appointment> {

}
