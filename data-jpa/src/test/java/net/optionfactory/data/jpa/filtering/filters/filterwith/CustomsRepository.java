package net.optionfactory.data.jpa.filtering.filters.filterwith;

import net.optionfactory.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomsRepository extends JpaRepository<CustomEntity, Long>, WhitelistFilteringRepository<CustomEntity> {
}
