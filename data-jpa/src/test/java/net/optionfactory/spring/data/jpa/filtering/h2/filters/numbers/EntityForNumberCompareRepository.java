package net.optionfactory.spring.data.jpa.filtering.h2.filters.numbers;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityForNumberCompareRepository extends JpaRepository<EntityForNumberCompare, Long>, WhitelistFilteringRepository<EntityForNumberCompare> {

}
