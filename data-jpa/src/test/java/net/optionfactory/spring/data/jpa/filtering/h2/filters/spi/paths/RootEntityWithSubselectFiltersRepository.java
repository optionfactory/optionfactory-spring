package net.optionfactory.spring.data.jpa.filtering.h2.filters.spi.paths;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RootEntityWithSubselectFiltersRepository extends JpaRepository<RootEntityWithSubselectFilters, Long>, WhitelistFilteringRepository<RootEntityWithSubselectFilters> {
}