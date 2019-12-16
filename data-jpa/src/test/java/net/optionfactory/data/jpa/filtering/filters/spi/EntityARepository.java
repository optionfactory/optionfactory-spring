package net.optionfactory.data.jpa.filtering.filters.spi;

import net.optionfactory.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityARepository extends JpaRepository<EntityA, Long>, WhitelistFilteringRepository<EntityA> {
}
