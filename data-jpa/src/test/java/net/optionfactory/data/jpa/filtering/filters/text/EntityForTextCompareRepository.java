package net.optionfactory.data.jpa.filtering.filters.text;

import net.optionfactory.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityForTextCompareRepository extends JpaRepository<EntityForTextCompare, Long>, WhitelistFilteringRepository<EntityForTextCompare> {


}
