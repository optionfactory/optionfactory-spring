package net.optionfactory.spring.data.jpa.filtering.h2.repro;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JoinThroughEmbeddableRepository extends JpaRepository<JoinThroughEmbeddableEntity, Long>, WhitelistFilteringRepository<JoinThroughEmbeddableEntity> {


}
