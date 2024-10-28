package net.optionfactory.spring.data.jpa.filtering.h2.filters.instant;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityForInstantRepository extends JpaRepository<EntityForInstant, Long>, WhitelistFilteringRepository<EntityForInstant> {
}
