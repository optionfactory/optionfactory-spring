package net.optionfactory.data.jpa.filtering.chaining;

import net.optionfactory.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformersRepository extends JpaRepository<Performer, Long>, WhitelistFilteringRepository<Performer> {

}
