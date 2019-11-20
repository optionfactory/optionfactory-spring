package net.optionfactory.data.jpa.filtering.filters.localdate;

import net.optionfactory.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityForLocalDateRepository extends JpaRepository<EntityForLocalDate, Long>, WhitelistFilteringRepository<EntityForLocalDate> {

}
