package net.optionfactory.spring.data.jpa.filtering.filters.localdate;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityForLocalDateRepository extends JpaRepository<EntityForLocalDate, Long>, WhitelistFilteringRepository<EntityForLocalDate> {

}
