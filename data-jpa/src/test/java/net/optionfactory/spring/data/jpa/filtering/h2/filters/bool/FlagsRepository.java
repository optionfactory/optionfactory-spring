package net.optionfactory.spring.data.jpa.filtering.h2.filters.bool;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlagsRepository extends JpaRepository<Flag, Long>, WhitelistFilteringRepository<Flag> {
}
