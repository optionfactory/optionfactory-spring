package net.optionfactory.data.jpa.filtering;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FlagsRepository extends JpaRepository<Flag, Long>, WhitelistFilteringRepository<Flag> {
}
