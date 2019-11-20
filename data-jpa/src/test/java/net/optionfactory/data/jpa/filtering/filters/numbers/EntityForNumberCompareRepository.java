package net.optionfactory.data.jpa.filtering.filters.numbers;

import net.optionfactory.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityForNumberCompareRepository extends JpaRepository<EntityForNumberCompare, Long>, WhitelistFilteringRepository<EntityForNumberCompare> {

}
