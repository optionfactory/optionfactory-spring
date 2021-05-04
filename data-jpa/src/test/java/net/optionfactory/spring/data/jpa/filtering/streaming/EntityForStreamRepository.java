package net.optionfactory.spring.data.jpa.filtering.streaming;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityForStreamRepository extends JpaRepository<EntityForStream, Long>, WhitelistFilteringRepository<EntityForStream> {


}
