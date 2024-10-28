package net.optionfactory.spring.data.jpa.filtering.h2.filters.filterwith;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomsRepository extends JpaRepository<CustomEntity, Long>, WhitelistFilteringRepository<CustomEntity> {
}
