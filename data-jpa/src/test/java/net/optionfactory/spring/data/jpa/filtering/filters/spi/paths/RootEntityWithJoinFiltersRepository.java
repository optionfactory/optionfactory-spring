package net.optionfactory.spring.data.jpa.filtering.filters.spi.paths;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RootEntityWithJoinFiltersRepository extends JpaRepository<RootEntityWithJoinFilters, Long>, WhitelistFilteringRepository<RootEntityWithJoinFilters> {
}
