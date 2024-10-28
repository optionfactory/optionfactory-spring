package net.optionfactory.spring.data.jpa.filtering.psql.json;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityWithEmbeddedJsonRepository extends JpaRepository<EntityWithEmbeddedJson, Long>, WhitelistFilteringRepository<EntityWithEmbeddedJson> {
}
