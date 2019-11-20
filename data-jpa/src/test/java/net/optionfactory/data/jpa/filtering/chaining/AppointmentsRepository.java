package net.optionfactory.data.jpa.filtering.chaining;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import net.optionfactory.data.jpa.filtering.FilterRequest;
import net.optionfactory.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentsRepository extends JpaRepository<Appointment, Long>, WhitelistFilteringRepository<Appointment> {

    Optional<Appointment> findByIdAndDate(long id, LocalDate date);

    Slice<Appointment> findByDate(LocalDate date, Pageable pr);

    default List<Appointment> findAllByDate(LocalDate date, FilterRequest fr) {
        return findAll((root, query, cb) -> cb.equal(root.get("date"), date), fr);
    }
}
