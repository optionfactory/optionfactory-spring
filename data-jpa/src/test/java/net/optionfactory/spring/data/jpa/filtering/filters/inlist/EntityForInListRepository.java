package net.optionfactory.spring.data.jpa.filtering.filters.inlist;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityForInListRepository extends JpaRepository<EntityForInList, Long>, WhitelistFilteringRepository<EntityForInList> {


}
