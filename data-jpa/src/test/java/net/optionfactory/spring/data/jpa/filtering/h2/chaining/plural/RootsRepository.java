package net.optionfactory.spring.data.jpa.filtering.h2.chaining.plural;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RootsRepository extends JpaRepository<Root, Long>, WhitelistFilteringRepository<Root> {

}
