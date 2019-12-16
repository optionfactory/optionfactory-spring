package net.optionfactory.data.jpa.filtering.filters.spi;

import net.optionfactory.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityBRepository extends JpaRepository<EntityB, Long>, WhitelistFilteringRepository<EntityB> {
}
