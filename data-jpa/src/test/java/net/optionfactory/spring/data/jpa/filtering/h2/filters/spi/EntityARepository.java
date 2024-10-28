package net.optionfactory.spring.data.jpa.filtering.h2.filters.spi;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityARepository extends JpaRepository<EntityA, Long>, WhitelistFilteringRepository<EntityA> {
}
