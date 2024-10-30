package net.optionfactory.spring.data.jpa.filtering.psql.examples;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetOwnersRepository extends JpaRepository<PetOwner, Long>, WhitelistFilteringRepository<PetOwner> {

}
