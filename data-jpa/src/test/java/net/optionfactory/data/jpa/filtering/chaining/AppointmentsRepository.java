package net.optionfactory.data.jpa.filtering.chaining;

import net.optionfactory.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentsRepository extends JpaRepository<Appointment, Long>, WhitelistFilteringRepository<Appointment> {

}
