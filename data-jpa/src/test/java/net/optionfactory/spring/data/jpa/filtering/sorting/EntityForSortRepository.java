package net.optionfactory.spring.data.jpa.filtering.sorting;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityForSortRepository extends JpaRepository<EntityForSort, Long>, WhitelistFilteringRepository<EntityForSort> {
}
