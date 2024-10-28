package net.optionfactory.spring.data.jpa.filtering.h2.filters.text;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityForTextCompareRepository extends JpaRepository<EntityForTextCompare, Long>, WhitelistFilteringRepository<EntityForTextCompare> {


}
